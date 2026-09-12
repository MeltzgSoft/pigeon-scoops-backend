(ns user
  (:require [clj-http.client :as http]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [integrant.core :as ig]
            [integrant.repl :as ig-repl]
            [integrant.repl.state :as state]
            [muuntaja.core :as m]
            [pigeon-scoops-backend.auth :as auth0]
            [pigeon-scoops-backend.utils :refer [init-system! load-config!]]
            [pigeon-scoops-backend.user-order.db :as order-db]
            [ring.mock.request :as mock]
            [clojure.pprint :refer [pprint]]
            [pigeon-scoops-backend.db]
            [pigeon-scoops-backend.db-tasks]
            [pigeon_scoops_backend.server])
  (:import (java.util UUID)
           (org.testcontainers.containers PostgreSQLContainer)))

(def users-config "dev/resources/repl-users.edn")
(def db-container (atom nil))
(def tokens (atom nil))
(def test-users (atom nil))

(defn get-token! [{:keys [test-client-id username password]}]
  (->> {:content-type  :json
        :cookie-policy :standard
        :body          (m/encode "application/json"
                                 {:client_id  test-client-id
                                  :audience   "https://pigeon-scoops.us.auth0.com/api/v2/"
                                  :grant_type "password"
                                  :username   username
                                  :password   password
                                  :scope      "openid profile email"})}
       (http/post "https://pigeon-scoops.us.auth0.com/oauth/token")
       (m/decode-response-body)
       :access_token))

(defn make-request!
  ([method uri]
   (make-request! method uri nil))
  ([method uri opts]
   (let [app (-> state/system :server/routes)
         auth (-> state/system :auth/auth0)
         response (app (-> (mock/request method uri)
                           (cond-> (:auth opts) (mock/header :authorization (str "Bearer " (or (first @tokens) (get-token! (conj auth (first @test-users))))))
                                   (:body opts) (mock/json-body (:body opts)))))
         response (update response :body #(try
                                            (m/decode "application/json" %)
                                            (catch Exception e
                                              (pprint {:body (:body response)
                                                       :status (:status response)
                                                       :cause (:cause e)})
                                              nil)))]

     response)))

(defn make-test-users! [n]
  (let [auth (:auth/auth0 state/system)
        usernames (repeatedly n #(str "repl-user" (UUID/randomUUID) "@pigeon-scoops.com"))
        passwords (repeatedly n #(str (UUID/randomUUID)))
        create-responses (mapv #(auth0/create-user! auth
                                                    {:connection "Username-Password-Authentication"
                                                     :email      %1
                                                     :password   %2})
                               usernames passwords)]
    (reset! test-users (mapv (fn [u p c]
                               {:username u
                                :password p
                                :uid      (:user_id c)})
                             usernames passwords create-responses))
    (mapv #(auth0/update-roles! auth
                                (:uid %)
                                [:manage-orders :manage-recipes :manage-groceries :manage-menus :manage-production])
          @test-users)
    (reset! tokens (mapv #(get-token! (conj auth %)) @test-users))
    (loop [response (make-request! :post "/v1/account" {:auth true})
           attempts 0]
      (when (= (:status response) 401)
        (println "attempt" attempts "failed")
        (Thread/sleep 1000)
        (recur (make-request! :post "/v1/account" {:auth true})
               (inc attempts))))
    (spit users-config (with-out-str
                         (pprint @test-users)))))

(defn load-test-user! []
  (println "loading token")
  (->> users-config
       (slurp)
       (edn/read-string)
       (reset! test-users)
       (first)
       (conj (:auth/auth0 state/system))
       (get-token!)
       (vector)
       (reset! tokens))
  (println "tokens" @tokens)
  (loop [response (make-request! :post "/v1/account" {:auth true})
         attempts 0]
    (when (= (:status response) 401)
      (println "attempt" attempts "failed")
      (println (select-keys response [:body :status]))
      (Thread/sleep 1000)
      (recur (make-request! :post "/v1/account" {:auth true})
             (inc attempts)))))

(defn load-seed-data! []
  (let [grocery-map (->> "dev/resources/seed/groceries.json"
                         (slurp)
                         (m/decode "application/json")
                         (map #(vector (:type %) (-> (make-request! :post "/v1/groceries"
                                                                    {:auth true
                                                                     :body {:grocery/name       (:type %)
                                                                            :grocery/department :department/grocery}})
                                                     :body
                                                     :id)))
                         (into {}))
        units (->> "dev/resources/seed/grocery_units.json"
                   (slurp)
                   (m/decode "application/json")
                   (map #(-> (make-request! :post (str "/v1/groceries/" (get grocery-map (:type %)) "/units")
                                            {:auth true
                                             :body (cond-> {:grocery-unit/source    (:source %)
                                                            :grocery-unit/unit-cost (:unit_cost %)}
                                                     (:unit_mass %) (merge {:grocery-unit/unit-mass      (:unit_mass %)
                                                                            :grocery-unit/unit-mass-type (keyword "mass" (:unit_mass_type %))})
                                                     (:unit_volume %) (merge {:grocery-unit/unit-volume      (:unit_volume %)
                                                                              :grocery-unit/unit-volume-type (keyword "volume" (:unit_volume_type %))})
                                                     (:unit_common %) (merge {:grocery-unit/unit-common      (:unit_common %)
                                                                              :grocery-unit/unit-common-type (keyword "common" (:unit_common_type %))}))})
                             :body
                             :id)))
        recipe-map (->> "dev/resources/seed/recipes.json"
                        (slurp)
                        (m/decode "application/json")
                        (map #(vector (:id %) (-> (make-request! :post "/v1/recipes"
                                                                 {:auth true
                                                                  :body (update-keys
                                                                         (merge (select-keys % [:name :instructions])
                                                                                {:amount      (:amount %)
                                                                                 :amount-unit (keyword (last (str/split (:amount_unit_type %) #"\."))
                                                                                                       (:amount_unit %))
                                                                                 :source      (or (:source %) "")})
                                                                         (fn [k] (keyword "recipe" (name k))))})
                                                  :body
                                                  :id)))
                        (into {}))
        ingredients (->> "dev/resources/seed/ingredients.json"
                         (slurp)
                         (m/decode "application/json")
                         (map #(-> (make-request! :post (str "/v1/recipes/" (get recipe-map (:recipe_id %)) "/ingredients")
                                                  {:auth true
                                                   :body (update-keys {:ingredient-grocery-id (get grocery-map (:ingredient_type %))
                                                                       :amount                (:amount %)
                                                                       :amount-unit           (keyword (last (str/split (:amount_unit_type %) #"\."))
                                                                                                       (:amount_unit %))}
                                                                      (fn [k] (keyword "ingredient" (name k))))})

                                   :body
                                   :id)))
        recipe-map (merge recipe-map
                          (->> "dev/resources/seed/flavors.json"
                               (slurp)
                               (m/decode "application/json")
                               (map #(let [recipe-id (-> (make-request! :post "/v1/recipes"
                                                                        {:auth true
                                                                         :body (update-keys (merge (select-keys % [:name :instructions])
                                                                                                   {:amount      (:amount %)
                                                                                                    :amount-unit (keyword (last (str/split (:amount_unit_type %) #"\."))
                                                                                                                          (:amount_unit %))
                                                                                                    :source      ""})
                                                                                            (fn [k] (keyword "recipe" (name k))))})
                                                         :body
                                                         :id)]
                                       (make-request! :post (str "/v1/recipes/" recipe-id "/ingredients")
                                                      {:auth true
                                                       :body (update-keys {:ingredient-recipe-id (get recipe-map (:recipe_id %))
                                                                           :amount               (:amount %)
                                                                           :amount-unit          (keyword (last (str/split (:amount_unit_type %) #"\."))
                                                                                                          (:amount_unit %))}
                                                                          (fn [k] (keyword "ingredient" (name k))))})

                                       [(:id %) recipe-id]))
                               (into {})))
        ingredients (concat ingredients
                            (->> "dev/resources/seed/mixins.json"
                                 (slurp)
                                 (m/decode "application/json")
                                 (map #(-> (make-request! :post (str "/v1/recipes/" (get recipe-map (:flavor_id %)) "/ingredients")
                                                          {:auth true
                                                           :body (update-keys {:ingredient-recipe-id (get recipe-map (:recipe_id %))
                                                                               :amount               (:amount %)
                                                                               :amount-unit          (keyword (last (str/split (:amount_unit_type %) #"\."))
                                                                                                              (:amount_unit %))}
                                                                              (fn [k] (keyword "ingredient" (name k))))})
                                           :body
                                           :id))))
        menu-id (-> (make-request! :post "/v1/menus"
                                   {:auth true
                                    :body {:menu/name          "test menu"
                                           :menu/active        true
                                           :menu/repeats       false
                                           :menu/duration      3
                                           :menu/duration-type :duration/day}})
                    :body
                    :id)
        menu-item-ids (map #(-> (make-request! :post (str "/v1/menus/" menu-id "/items")
                                               {:auth true
                                                :body {:menu-item/recipe-id %}})
                                :body
                                :id)
                           (take 2 (vals recipe-map)))
        menu-item-size-ids (doall (map #(-> (make-request! :post (str "/v1/menus/" menu-id "/items/" (first %) "/sizes")
                                                           {:auth true
                                                            :body {:menu-item-size/amount (second %)
                                                                   :menu-item-size/amount-unit :volume/qt}})
                                            :body
                                            :id)
                                       (for [miid menu-item-ids
                                             size (range 1 3)]
                                         (vector miid size))))
        menu (-> (make-request! :get (str "/v1/menus/" menu-id)
                                {:auth true})
                 :body)
        order-map (->> "dev/resources/seed/orders.json"
                       (slurp)
                       (m/decode "application/json")
                       (map #(let [pair (vector (:id %) (-> (make-request! :post "/v1/orders"
                                                                           {:auth true
                                                                            :body (update-keys % (fn [k] (keyword "user-order" (name k))))})
                                                            :body
                                                            :id))]
                               (Thread/sleep 500)
                               pair))
                       (into {}))
        order-items (->> "dev/resources/seed/flavor_amounts.json"
                         (slurp)
                         (m/decode "application/json")
                         (mapv #(-> (make-request! :post (str "/v1/orders/" (get order-map (:order_id %)) "/items")
                                                   {:auth true
                                                    :body {:order-item/recipe-id   (get recipe-map (:flavor_id %))
                                                           :order-item/status :status/submitted
                                                           :order-item/amount      (:amount %)
                                                           :order-item/amount-unit (keyword (last (str/split (:amount_unit_type %) #"\."))
                                                                                            (:amount_unit %))}})
                                    :body
                                    :id)))
        results {:grocery-map   grocery-map
                 :grocery-units units
                 :recipe-map    recipe-map
                 :ingredients   ingredients
                 :order-map     order-map
                 :order-items   order-items
                 :menu-id       menu-id
                 :menu-item-ids menu-item-ids
                 :mnu-item-size-ids menu-item-size-ids
                 :complete-order-item-statuses
                 (->> (make-request! :get "/v1/orders?detailed=true&admin=true"
                                     {:auth true})
                      :body
                      (mapcat :user-order/items)
                      (map #(:status (make-request! :patch (str "/v1/orders/" (:order-item/order-id %) "/items/" (:order-item/id %) "/status")
                                                    {:auth true
                                                     :body {:order-item/status :status/complete}}))))}]
    (let [new-order-id (-> (make-request! :post "/v1/orders"
                                          {:auth true
                                           :body {:user-order/note "from current menu"}})
                           :body
                           :id)]
      (->> menu
           :menu/items
           (mapcat #(->> %
                         :menu-item/sizes
                         (map (fn [size]
                                (assoc size :menu-item-size-id/recipe-id (:menu-item/recipe-id %))))))
           (map #(make-request! :post (str "/v1/orders/" new-order-id "/items")
                                {:auth true
                                 :body {:order-item/recipe-id   (:menu-item-size-id/recipe-id %2)
                                        :order-item/status :status/submitted
                                        :order-item/amount      (* (inc %1) (:menu-item-size/amount %2))
                                        :order-item/amount-unit (:menu-item-size/amount-unit %2)
                                        :order-item/menu-item-size-id (:menu-item-size/id %2)}})
                (range))
           (map #(->> %
                      :body
                      :id
                      ((fn [order-item-id]
                         (make-request! :patch (str "/v1/orders/" new-order-id "/items/" order-item-id "/status")
                                        {:auth true
                                         :body {:order-item/status :status/in-progress}})))))

           (pprint)))
    results))

(defn init-app! []
  (if (.exists (io/file users-config))
    (load-test-user!)
    (make-test-users! 1))
  (load-seed-data!))

(ig-repl/set-prep!
 (fn []
   (when-not @db-container
     (println "resetting db")
     (reset! db-container (doto (PostgreSQLContainer. "postgres:latest") ;; Full reference to PostgreSQLContainer
                            (.withDatabaseName "test_db")
                            (.withUsername "user")
                            (.withPassword "password")
                            (.start))))
   (let [task-system (-> "resources/db-task-config.edn"
                         (load-config!)
                         (update-in [:db/postgres :jdbc-url] #(if (nil? %)
                                                                (str (.getJdbcUrl @db-container)
                                                                     "&user=" (.getUsername @db-container)
                                                                     "&password=" (.getPassword @db-container))
                                                                %))
                         (init-system!))
         migration-task (:db-tasks/migration task-system)]
     (println "JDBC-URI:" (str (.getJdbcUrl @db-container)
                               "&user=" (.getUsername @db-container)
                               "&password=" (.getPassword @db-container)))
     (migration-task)
     (ig/halt! task-system))
   (-> "dev/resources/server-config.edn"
       (load-config!)
       (update-in [:db/postgres :jdbc-url] #(if (nil? %)
                                              (str (.getJdbcUrl @db-container)
                                                   "&user=" (.getUsername @db-container)
                                                   "&password=" (.getPassword @db-container))
                                              %))
       (ig/expand))))

(defn go!
  ([]
   (go! true))
  ([init-db?]
   (ig-repl/go)
   (when init-db? (init-app!))))
(defn halt! []
  (ig-repl/halt))
(defn ig-reset! []
  (ig-repl/reset)
  (init-app!))
(defn ig-reset-all! []
  (ig-repl/reset-all)
  (init-app!))

(comment
  (map #(auth0/delete-user! (:auth/auth0 state/system) (:user_id %))
       (filter #(str/starts-with? (:email %) "integration-test")
               (auth0/get-users! (:auth/auth0 state/system))))
  (-> (make-request! :get "/v1/orders?detailed=true&admin=true"
                     {:auth true})
      :body
      (first)
      :user-order/id
      (#(make-request! :get (str "/v1/orders/" %)
                       {:auth true})))
  (.getJdbcUrl (:connectable (:db/postgres state/system)))
  (make-test-users! 2)
  (order-db/find-all-orders! (:db/postgres state/system) nil true)
  (go!)
  (go! false)
  (pprint @tokens)
  (halt!)
  (ig-reset!)
  (ig-reset-all!))
