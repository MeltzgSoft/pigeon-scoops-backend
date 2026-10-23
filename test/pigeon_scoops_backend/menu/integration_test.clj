(ns pigeon-scoops-backend.menu.integration-test
  (:require [clojure.test :refer [are deftest is testing use-fixtures]]
            [pigeon-scoops-backend.test-system :as ts]))

(use-fixtures :once ts/system-fixture (ts/make-account-fixture) (ts/make-roles-fixture :manage-recipes :manage-menus))

(def menu
  {:menu/name          "test menu"
   :menu/repeats       true
   :menu/active        false
   :menu/duration      3
   :menu/duration-type :duration/month})

(def recipe
  {:recipe/name         "a spicy meatball"
   :recipe/amount       3
   :recipe/amount-unit  :mass/lb
   :recipe/source       "the book"
   :recipe/instructions ["make them"]})

(deftest menus-list-test
  (let [active-menu-id   (-> (ts/test-endpoint :post "/v1/menus" {:use-auth? true :body (assoc menu :menu/active true)})
                             :body
                             :id
                             (parse-uuid))
        inactive-menu-id (-> (ts/test-endpoint :post "/v1/menus" {:use-auth? true :body (assoc menu :menu/active false)})
                             :body
                             :id
                             (parse-uuid))
        recipe-id        (-> (ts/test-endpoint :post "/v1/recipes" {:use-auth? true :body recipe})
                             :body
                             :id)]
    (mapv #(ts/test-endpoint :post (str "/v1/menus/" % "/items")
                             {:use-auth? true :body {:menu-item/recipe-id recipe-id}})
          [active-menu-id inactive-menu-id])
    (testing "List menus"
      (are [include-inactive? detailed?]
           (let [{:keys [status body]} (ts/test-endpoint :get "/v1/menus" {:use-auth? true
                                                                           :params    {:include-inactive include-inactive?
                                                                                       :detailed         detailed?}})
                 body                  (filter #(#{active-menu-id inactive-menu-id} (parse-uuid (:menu/id %))) body)
                 conditions            {:status       (= status 200)
                                        :not-empty    (pos? (count body))
                                        :has-active   (some #(= (parse-uuid (:menu/id %)) active-menu-id) body)
                                        :has-inactive (= include-inactive? (true? (some #(= (parse-uuid (:menu/id %)) inactive-menu-id) body)))
                                        :detailed     (every? #(= detailed? (not= (count (:menu/items %)) 0))
                                                              body)}]
             (every? true? (vals conditions)))
        true true
        true false
        false true
        false false))))

(deftest menus-crud-test
  (let [menu-id           (atom nil)
        menu-item-id      (atom nil)
        menu-item-size-id (atom nil)
        recipe-ids        (map #(get-in % [:body :id]) (repeatedly 2 #(ts/test-endpoint :post "/v1/recipes" {:use-auth? true :body recipe})))]
    (testing "create inactive menu"
      (let [{:keys [status body]} (ts/test-endpoint :post "/v1/menus" {:use-auth? true :body menu})
            {menu-body :body}     (ts/test-endpoint :get (str "/v1/menus/" (:id body))
                                                    {:use-auth? true})]
        (reset! menu-id (:id body))
        (is (= status 201))
        (is (nil? (:menu/end-time menu-body)))))
    (testing "create active menu"
      (let [{:keys [status body]} (ts/test-endpoint :post "/v1/menus" {:use-auth? true :body (assoc menu :menu/active true)})
            {menu-body :body}     (ts/test-endpoint :get (str "/v1/menus/" (:id body))
                                                    {:use-auth? true})]
        (is (= status 201))
        (is (some? (:menu/end-time menu-body)))))
    (let [original-endtime (atom nil)]
      (testing "activate menu populates end time"
        (let [{:keys [status]}  (ts/test-endpoint
                                 :put
                                 (str "/v1/menus/" @menu-id)
                                 {:use-auth? true :body (assoc menu :menu/active true)})
              {menu-body :body} (ts/test-endpoint :get (str "/v1/menus/" @menu-id)
                                                  {:use-auth? true})]
          (is (= status 204))
          (is (some? (:menu/end-time menu-body)))
          (reset! original-endtime (:menu/end-time menu-body))))
      (testing "update menu"
        (let [{:keys [status]}  (ts/test-endpoint
                                 :put
                                 (str "/v1/menus/" @menu-id)
                                 {:use-auth? true :body (assoc menu :menu/duration 4 :menu/active true)})
              {menu-body :body} (ts/test-endpoint :get (str "/v1/menus/" @menu-id)
                                                  {:use-auth? true})]
          (is (= status 204))
          (is (= (:menu/end-time menu-body) @original-endtime)))))
    (testing "deactivate menu nils end time"
      (let [{:keys [status]}  (ts/test-endpoint
                               :put
                               (str "/v1/menus/" @menu-id)
                               {:use-auth? true :body (assoc menu :menu/active false)})
            {menu-body :body} (ts/test-endpoint :get (str "/v1/menus/" @menu-id)
                                                {:use-auth? true})]
        (is (= status 204))
        (is (nil? (:menu/end-time menu-body)))))
    (testing "create menu-item"
      (let [{:keys [status body]} (ts/test-endpoint :post (str "/v1/menus/" @menu-id "/items")
                                                    {:use-auth? true :body {:menu-item/recipe-id (first recipe-ids)}})]
        (reset! menu-item-id (:id body))
        (is (= status 201))))
    (testing "update menu-item"
      (let [{:keys [status]} (ts/test-endpoint :put (str "/v1/menus/" @menu-id "/items/" @menu-item-id)
                                               {:use-auth? true :body {:menu-item/recipe-id (second recipe-ids)}})]
        (is (= status 204))))
    (testing "create menu item size"
      (let [{:keys [status body]} (ts/test-endpoint :post (str "/v1/menus/" @menu-id "/items/" @menu-item-id "/sizes")
                                                    {:use-auth? true :body {:menu-item-size/amount             4
                                                                            :menu-item-size/amount-unit        :mass/lb
                                                                            :menu-item-size/available-quantity 10}})]
        (reset! menu-item-size-id (:id body))
        (is (= status 201) (str "body " body))))
    (testing "update menu item size"
      (let [{:keys [status] :as resp} (ts/test-endpoint :put (str "/v1/menus/" @menu-id "/items/" @menu-item-id "/sizes/" @menu-item-size-id)
                                                        {:use-auth? true :body {:menu-item-size/amount      3
                                                                                :menu-item-size/amount-unit :volume/gal}})]
        (is (= status 204) (str resp))))
    (testing "retrieve menu includes available-quantity on sizes"
      (let [{:keys [status body]} (ts/test-endpoint :get (str "/v1/menus/" @menu-id)
                                                    {:use-auth? true})
            sizes                 (->> body :menu/items (mapcat :menu-item/sizes))]
        (is (= status 200))
        (is (every? #(contains? % :menu-item-size/available-quantity) sizes))))
    (testing "retrieve full menu"
      (let [{:keys [status]} (ts/test-endpoint :get (str "/v1/menus/" @menu-id)
                                               {:use-auth? true})]
        (is (= status 200))))
    (let [{:keys [body]} (ts/test-endpoint :post "/v1/menus" {:use-auth? true :body menu})
          other-menu-id  (:id body)]
      (testing "cannot update menu item without matching menu id"
        (let [{:keys [status]} (ts/test-endpoint :put (str "/v1/menus/" other-menu-id "/items/" @menu-item-id)
                                                 {:use-auth? true :expected-status 400 :body {:menu-item/recipe-id (second recipe-ids)}})]
          (is (= status 400))))
      (testing "cannot update menu item size without matching menu id"
        (let [{:keys [status]} (ts/test-endpoint :put (str "/v1/menus/" other-menu-id "/items/" @menu-item-id "/sizes/" @menu-item-size-id)
                                                 {:use-auth? true :expected-status 400 :body {:menu-item-size/amount      3
                                                                                              :menu-item-size/amount-unit :volume/gal}})]
          (is (= status 400))))
      (testing "cannot delete menu item size without matching menu id"
        (let [{:keys [status]} (ts/test-endpoint :delete (str "/v1/menus/" other-menu-id "/items/" @menu-item-id "/sizes/" @menu-item-size-id)
                                                 {:use-auth? true :expected-status 400})]
          (is (= status 400))))
      (testing "cannot delete menu item without matching menu id"
        (let [{:keys [status]} (ts/test-endpoint :delete (str "/v1/menus/" other-menu-id "/items/" @menu-item-id)
                                                 {:use-auth? true :expected-status 400})]
          (is (= status 400)))))
    (testing "delete menu item size"
      (let [{:keys [status]} (ts/test-endpoint :delete (str "/v1/menus/" @menu-id "/items/" @menu-item-id "/sizes/" @menu-item-size-id)
                                               {:use-auth? true})]
        (is (= status 204))))
    (testing "delete menu item"
      (let [{:keys [status]} (ts/test-endpoint :delete (str "/v1/menus/" @menu-id "/items/" @menu-item-id)
                                               {:use-auth? true})]
        (is (= status 204))))
    (testing "delete menu"
      (let [{:keys [status]} (ts/test-endpoint :delete (str "/v1/menus/" @menu-id) {:use-auth? true})]
        (is (= status 204))))))
