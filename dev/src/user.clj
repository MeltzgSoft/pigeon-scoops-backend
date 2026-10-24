(ns user
  (:require [clj-http.client :as http]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [integrant.core :as ig]
            [integrant.repl :as ig-repl]
            [integrant.repl.state :as state]
            [muuntaja.core :as mtj]
            [malli.core :as m]
            [pigeon-scoops-backend.auth :as auth0]
            [pigeon-scoops-backend.utils :refer [init-system! load-config!]]
            [ring.mock.request :as mock]
            [clojure.pprint :refer [pprint]]
            [pigeon-scoops-backend.db]
            [pigeon-scoops-backend.db-tasks]
            [pigeon_scoops_backend.server]
            [pigeon-scoops-backend.units.common :as common])
  (:import (java.util UUID)
           (org.testcontainers.containers PostgreSQLContainer)))

(def users-config "dev/resources/repl-users.edn")
(def db-container (atom nil))
(def tokens (atom nil))
(def test-users (atom nil))

(defn get-token! [{:keys [test-client-id username password]}]
  (->> {:content-type  :json
        :cookie-policy :standard
        :body          (mtj/encode "application/json"
                                   {:client_id  test-client-id
                                    :audience   "https://pigeon-scoops.us.auth0.com/api/v2/"
                                    :grant_type "password"
                                    :username   username
                                    :password   password
                                    :scope      "openid profile email"})}
       (http/post "https://pigeon-scoops.us.auth0.com/oauth/token")
       (mtj/decode-response-body)
       :access_token))

(defn make-request!
  ([method uri]
   (make-request! method uri nil))
  ([method uri opts]
   (let [app      (-> state/system :server/routes)
         auth     (-> state/system :auth/auth0)
         response (app (-> (mock/request method uri)
                           (cond-> (:auth opts) (mock/header :authorization (str "Bearer " (or (first @tokens) (get-token! (conj auth (first @test-users))))))
                                   (:body opts) (mock/json-body (:body opts)))))
         response (update response :body #(try
                                            (mtj/decode "application/json" %)
                                            (catch Exception e
                                              (pprint {:body   (:body response)
                                                       :status (:status response)
                                                       :cause  (:cause e)})
                                              nil)))]

     response)))

(defn make-test-users! [n]
  (let [auth             (:auth/auth0 state/system)
        usernames        (repeatedly n #(str "repl-user" (UUID/randomUUID) "@pigeon-scoops.com"))
        passwords        (repeatedly n #(str (UUID/randomUUID)))
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

(defn load-entities!
  ([entities entity-key route]
   (load-entities! entities entity-key route nil))
  ([entities entity-key route subentity-key]
   (->> entities
        (map (fn [entity]
               (->> (make-request! :post
                                   (str "/v1/" route)
                                   {:auth true :body entity})
                    :body
                    :id
                    (hash-map :old-id ((keyword (name entity-key) "id") entity) :new-id)
                    (#(if subentity-key (assoc % subentity-key (subentity-key entity)) %))
                    (vector ((keyword (name entity-key) "id") entity)))))
        (into {}))))

(defn load-seed-data! []
  (let [seed-map (reduce
                  (fn [acc [entity-key route subentity-key]]
                    (update acc entity-key load-entities! entity-key route subentity-key))
                  (-> "dev/resources/seed.edn"
                      (slurp)
                      (edn/read-string))
                  [[:grocery "groceries" :grocery/units]
                   [:recipe "recipes" :recipe/ingredients]
                   [:menu "menus" :menu/items]
                   [:user-order "orders" :user-order/items]])]
    (-> seed-map
        (update
         :grocery
         (fn [groceries]
           (update-vals
            groceries
            (fn [grocery]
              (update grocery
                      :grocery/units
                      load-entities!
                      :grocery-unit
                      (str "groceries/" (:new-id grocery) "/units"))))))
        (update
         :recipe
         (fn [recipes]
           (update-vals
            recipes
            (fn [recipe]
              (update recipe
                      :recipe/ingredients
                      (fn [ingredients]
                        (load-entities!
                         (map (fn [ingredient]
                                (cond-> ingredient
                                  (:ingredient/ingredient-grocery-id ingredient)
                                  (update :ingredient/ingredient-grocery-id #(get-in seed-map [:grocery % :new-id]))
                                  (:ingredient/ingredient-recipe-id ingredient)
                                  (update :ingredient/ingredient-recipe-id #(get-in seed-map [:recipe % :new-id]))))
                              ingredients)
                         :ingredient
                         (str "recipes/" (:new-id recipe) "/ingredients"))))))))
        (update
         :menu
         (fn [menus]
           (update-vals
            menus
            (fn [menu]
              (update menu
                      :menu/items
                      (fn [menu-items]
                        (let [item-map (load-entities!
                                        (map (fn [menu-item]
                                               (update menu-item :menu-item/recipe-id
                                                       #(get-in seed-map [:recipe % :new-id])))
                                             menu-items)
                                        :menu-item
                                        (str "menus/" (:new-id menu) "/items")
                                        :menu-item/sizes)]
                          (update-vals
                           item-map
                           (fn [menu-item]
                             (update menu-item :menu-item/sizes
                                     load-entities! :menu-item-size (str "menus/" (:new-id menu) "/items/" (:new-id menu-item) "/sizes")))))))))))

        (update
         :user-order
         (fn [orders]
           (update-vals
            orders
            (fn [order]
              (update order
                      :user-order/items
                      (fn [order-items]
                        (let [loaded-items (load-entities!
                                            (map (fn [order-item]
                                                   (cond-> order-item
                                                     (:order-item/recipe-id order-item)
                                                     (update :order-item/recipe-id #(get-in seed-map [:recipe % :new-id]))
                                                     (:order-item/menu-item-size-id order-item)
                                                     (assoc :order-item/menu-item-size-id (->> seed-map
                                                                                               :menu
                                                                                               (vals)
                                                                                               (mapcat :menu/items)
                                                                                               (mapcat :menu-item/sizes)
                                                                                               (filter (comp (partial = (:order-item/menu-item-size-id order-item))
                                                                                                             :order-item/menu-item-size-id))
                                                                                               (first)
                                                                                               :new-id))))
                                                 order-items)
                                            :order-item
                                            (str "orders/" (:new-id order) "/items"))]
                          (pprint (mapv #(make-request! :patch (str "/v1/orders/" (:new-id order) "/items/" (:new-id %) "/status")
                                                        {:auth true :body {:order-item/status :status/complete}})
                                        (vals loaded-items)))
                          loaded-items))))))))))

(defn init-app! []
  (if (.exists (io/file users-config))
    (load-test-user!)
    (make-test-users! 1))
  (load-seed-data!))

(ig-repl/set-prep!
 (fn []
   (let [base-config    (load-config! "resources/server-config.edn")
         _              (pprint base-config)
         jdbc-url       (or (-> base-config :db/postgres :jdbc-url)
                            (do
                              (when (nil? @db-container)
                                (println "resetting db")
                                (reset! db-container (doto (PostgreSQLContainer. "postgres:latest")
                                                       (.withDatabaseName "test_db")
                                                       (.withUsername "user")
                                                       (.withPassword "password")
                                                       (.start))))
                              (str (.getJdbcUrl @db-container)
                                   "&user=" (.getUsername @db-container)
                                   "&password=" (.getPassword @db-container))))
         task-system    (-> "resources/db-task-config.edn"
                            (load-config!)
                            (assoc-in [:db/postgres :jdbc-url] jdbc-url)
                            (init-system!))
         migration-task (:db-tasks/migration task-system)]
     (println "JDBC-URI:" (str (.getJdbcUrl @db-container)
                               "&user=" (.getUsername @db-container)
                               "&password=" (.getPassword @db-container)))
     (migration-task)
     (ig/halt! task-system)
     (-> base-config
         (assoc-in [:db/postgres :jdbc-url] jdbc-url)
         (ig/expand)))))

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
  (spit
   "dev/resources/seed.edn"
   (with-out-str
     (pprint
      (into
       {}
       (map
        (fn [[k v]]
          (->> (make-request! :get (str "/v1/" v) {:auth true})
               :body
               (map (keyword (name k) "id"))
               (map #(make-request! :get (str "/v1/" v "/" %) {:auth true}))
               (map :body)
               (vector k)))
        {:recipe     "recipes"
         :grocery    "groceries"
         :user-order "orders"})))))
  (common/convert 1 :volume/floz :volume/tbsp)
  (m/coerce :string 22)
  (go!)
  (halt!)
  (ig-reset!)
  (ig-reset-all!))
