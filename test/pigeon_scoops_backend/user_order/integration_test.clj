(ns pigeon-scoops-backend.user-order.integration-test
  (:require [clojure.test :refer [are deftest is testing use-fixtures]]
            [integrant.repl.state :as state]
            [pigeon-scoops-backend.test-system :as ts]
            [pigeon-scoops-backend.user-order.db :as order-db]
            [pigeon-scoops-backend.user-order.schema :refer [status terminal?]]))

(use-fixtures :once ts/system-fixture (ts/make-account-fixture) (ts/make-roles-fixture [:manage-recipes :manage-orders :manage-menus :manage-production] [:manage-recipes :manage-orders :manage-menus]))

(def order
  {:user-order/note "my order"})

(def updated-order
  (assoc order :user-order/note "still my order"))

(def order-item
  {:order-item/amount      1
   :order-item/amount-unit :volume/qt})

(def updated-order-item
  (assoc order-item :order-item/amount 3))

(def recipe
  {:recipe/name         "a spicy meatball"
   :recipe/amount       3
   :recipe/amount-unit  :mass/lb
   :recipe/source       "the book"
   :recipe/instructions ["make them"]})

(def menu
  {:menu/name          "test menu"
   :menu/repeats       true
   :menu/active        true
   :menu/duration      3
   :menu/duration-type :duration/month})

(deftest orders-list-test
  (let [recipe-id      (get-in (ts/test-endpoint :post "/v1/recipes" {:use-auth? true :body recipe}) [:body :id])
        menu-id        (-> (ts/test-endpoint :post "/v1/menus" {:use-auth? true :body menu})
                           :body
                           :id)
        menu-item-id   (-> (ts/test-endpoint :post (str "/v1/menus/" menu-id "/items")
                                             {:use-auth? true :body {:menu-item/recipe-id recipe-id}})
                           :body
                           :id)
        admin-order-id (-> (ts/test-endpoint :post "/v1/orders" {:use-auth? true :body order})
                           :body
                           :id
                           (parse-uuid))
        other-order-id (-> (ts/test-endpoint :post "/v1/orders" {:use-auth? true :body order :use-other-user true})
                           :body
                           :id
                           (parse-uuid))]
    (ts/test-endpoint :post (str "/v1/menus/" menu-id "/items/" menu-item-id "/sizes")
                      {:use-auth? true :body {:menu-item-size/amount      1
                                              :menu-item-size/amount-unit :volume/qt}})
    (mapv (fn [[order-id use-other-user?]]
            (ts/test-endpoint :post (str "/v1/orders/" order-id "/items")
                              {:use-auth?      true
                               :use-other-user use-other-user?
                               :body           (assoc order-item :order-item/recipe-id recipe-id)}))
          [[admin-order-id false] [other-order-id true]])
    (testing "List orders"
      (let [{:keys [status body]} (ts/test-endpoint :get "/v1/orders" {:use-auth? true})
            body                  (map #(update % :user-order/id parse-uuid) body)]
        (is (= 200 status))
        (is (pos? (count body)))
        (is ((set (map :user-order/id body)) admin-order-id))
        (is (not ((set (map :user-order/id body)) other-order-id)))
        (is (every? nil? (map :user-order/items body)))))
    (testing "List orders with details"
      (let [{:keys [status body]} (ts/test-endpoint :get "/v1/orders?detailed=true&admin=true" {:use-auth? true})
            body                  (filter #(#{admin-order-id other-order-id} (parse-uuid (:user-order/id %))) body)]
        (is (= 200 status))
        (is (pos? (count body)))
        (is ((set (map (comp parse-uuid :user-order/id) body)) admin-order-id))
        (is ((set (map (comp parse-uuid :user-order/id) body)) other-order-id))
        (is (every? seq (map :user-order/items body)))))
    (testing "List orders. admins can get orders from all users"
      (let [{:keys [status body]} (ts/test-endpoint :get "/v1/orders" {:use-auth? true :params {:admin true}})
            body                  (map #(update % :user-order/id parse-uuid) body)]
        (is (= 200 status))
        (is (pos? (count body)))
        (is ((set (map :user-order/id body)) admin-order-id))
        (is ((set (map :user-order/id body)) other-order-id))))
    (testing "List orders. non authenticated users cannot get orders from all users"
      (let [{:keys [status body]} (ts/test-endpoint :get "/v1/orders" {:use-auth? false})
            body                  (map #(update % :user-order/id parse-uuid) body)]
        (is (= 200 status))
        (is (zero? (count body)))))
    (testing "List orders. non admins cannot get orders from all users"
      (let [{:keys [status body]} (ts/test-endpoint :get "/v1/orders" {:use-auth? true :use-other-user true :params {:admin true}})
            body                  (map #(update % :user-order/id parse-uuid) body)]
        (is (= 200 status))
        (is (pos? (count body)))
        (is (not ((set (map :user-order/id body)) admin-order-id)))
        (is ((set (map :user-order/id body)) other-order-id))))))

(deftest orders-crud-test
  (let [order-id          (atom nil)
        order-item-id     (atom nil)
        recipe-id         (get-in (ts/test-endpoint :post "/v1/recipes" {:use-auth? true :body recipe}) [:body :id])
        other-recipe-id   (get-in (ts/test-endpoint :post "/v1/recipes" {:use-auth? true :body recipe}) [:body :id])
        ;; Create an active menu and add the recipe to it
        menu-id           (get-in (ts/test-endpoint :post "/v1/menus" {:use-auth? true :body menu}) [:body :id])
        menu-item-id      (get-in (ts/test-endpoint :post (str "/v1/menus/" menu-id "/items")
                                                    {:use-auth? true :body {:menu-item/recipe-id recipe-id}})
                                  [:body :id])
        menu-item-size-id (get-in (ts/test-endpoint :post (str "/v1/menus/" menu-id "/items/" menu-item-id "/sizes")
                                                    {:use-auth? true :body {:menu-item-size/menu-item-id menu-item-id
                                                                            :menu-item-size/amount       1
                                                                            :menu-item-size/amount-unit  :volume/pt}})
                                  [:body :id])]
    (testing "create order"
      (let [{:keys [status body]} (ts/test-endpoint :post "/v1/orders" {:use-auth? true :body order})]
        (reset! order-id (:id body))
        (is (= status 201))))
    (testing "update order"
      (let [{:keys [status]} (ts/test-endpoint :put (str "/v1/orders/" @order-id) {:use-auth? true :body updated-order})]
        (is (= status 204))))
    (testing "create order-item"
      (let [{:keys [status body]} (ts/test-endpoint :post (str "/v1/orders/" @order-id "/items")
                                                    {:use-auth? true :body (assoc order-item :order-item/recipe-id recipe-id)})]
        (reset! order-item-id (:id body))
        (is (= status 201))))
    (testing "production manager can create order-item for recipe not in an active menu"
      (let [{:keys [status]} (ts/test-endpoint :post (str "/v1/orders/" @order-id "/items")
                                               {:use-auth? true :body (assoc order-item :order-item/recipe-id other-recipe-id)})]
        (is (= status 201))))
    (testing "production manager can create order-item for an invalid size"
      (let [{:keys [status]} (ts/test-endpoint :post (str "/v1/orders/" @order-id "/items")
                                               {:use-auth? true :body (assoc order-item :order-item/recipe-id recipe-id :order-item/amount-unit :volume/c)})]
        (is (= status 201))))
    (testing "update order-item"
      (let [{:keys [status]} (ts/test-endpoint :patch (str "/v1/orders/" @order-id "/items/" @order-item-id)
                                               {:use-auth?       true :body (assoc updated-order-item
                                                                             :order-item/recipe-id recipe-id)
                                                :expected-status 204})]
        (is (= status 204))))
    (testing "production manager can update order-item for recipe not in an active menu"
      (let [{:keys [status]} (ts/test-endpoint :patch (str "/v1/orders/" @order-id "/items/" @order-item-id)
                                               {:use-auth?       true :body (assoc order-item
                                                                             :order-item/recipe-id other-recipe-id)
                                                :expected-status 204})]
        (is (= status 204))))
    (testing "production manager can update order-item for an invalid size"
      (let [{:keys [status]} (ts/test-endpoint :patch (str "/v1/orders/" @order-id "/items/" @order-item-id)
                                               {:use-auth?       true :body (assoc order-item
                                                                             :order-item/recipe-id recipe-id
                                                                             :order-item/amount-unit :volume/c)
                                                :expected-status 204})]
        (is (= status 204))))
    (testing "update order-item status"
      (let [{:keys [status]} (ts/test-endpoint :patch (str "/v1/orders/" @order-id "/items/" @order-item-id "/status")
                                               {:use-auth?       true :body {:order-item/status :status/complete}
                                                :expected-status 204})]
        (is (= status 204))))
    (testing "cannot delete orders with items"
      (let [{:keys [status]} (ts/test-endpoint :delete (str "/v1/orders/" @order-id)
                                               {:use-auth?       true
                                                :expected-status 400})]
        (is (= status 400))))
    (testing "can delete orders with no items"
      (->> (ts/test-endpoint :get (str "/v1/orders/" @order-id) {:use-auth? true})
           :body
           :user-order/items
           (map #(-> %
                     :order-item/id
                     ((fn [item-id]
                        (ts/test-endpoint :patch (str "/v1/orders/" @order-id "/items/" item-id "/status")
                                          {:use-auth? true :body {:order-item/status :status/draft} :expected-status 204})
                        (ts/test-endpoint :delete (str "/v1/orders/" @order-id "/items/" item-id)
                                          {:use-auth? true})))))
           (dorun))
      (let [{:keys [status]} (ts/test-endpoint :delete (str "/v1/orders/" @order-id)
                                               {:use-auth? true})]
        (is (= status 204))))
    (let [{:keys [body]} (ts/test-endpoint :post "/v1/orders" {:use-auth? true :use-other-user true :body order})
          order-id       (:id body)
          order-item-id  (atom nil)]
      (testing "other user can create order item for active recipe"
        (let [{:keys [status body]} (ts/test-endpoint :post (str "/v1/orders/" order-id "/items")
                                                      {:use-auth? true :use-other-user true
                                                       :body
                                                       (assoc order-item :order-item/recipe-id recipe-id
                                                              :order-item/menu-item-size-id menu-item-size-id)})
              {order-body :body}    (ts/test-endpoint :get (str "/v1/orders/" order-id)
                                                      {:use-auth? true :use-other-user true})]
          (reset! order-item-id (:id body))
          (is (= status 201) (str body))
          (is (= (-> order-body
                     :user-order/items
                     (first)
                     :order-item/menu-item-size-quantity)
                 2.0))))
      (testing "other user cannot create order-item for recipe not in an active menu"
        (let [{:keys [status body]} (ts/test-endpoint :post (str "/v1/orders/" order-id "/items")
                                                      {:use-auth?       true
                                                       :use-other-user  true
                                                       :expected-status 400
                                                       :body            (assoc order-item :order-item/recipe-id other-recipe-id)})]
          (is (= status 400) (str body))))
      (testing "other user cannot create order-item for an invalid size"
        (let [{:keys [status]} (ts/test-endpoint :post (str "/v1/orders/" order-id "/items")
                                                 {:use-auth?       true
                                                  :use-other-user  true
                                                  :expected-status 400
                                                  :body            (assoc order-item :order-item/recipe-id recipe-id :order-item/amount-unit :volume/c)})]
          (is (= status 400))))
      (testing "other user cannot update order-item for recipe not in an active menu"
        (let [{:keys [status]} (ts/test-endpoint :patch (str "/v1/orders/" order-id "/items/" @order-item-id)
                                                 {:use-auth?       true :use-other-user true :body (assoc order-item
                                                                                                    :order-item/recipe-id other-recipe-id)
                                                  :expected-status 400})]
          (is (= status 400))))
      (testing "other user cannot update order-item for an invalid size"
        (let [{:keys [status]} (ts/test-endpoint :patch (str "/v1/orders/" order-id "/items/" @order-item-id)
                                                 {:use-auth?       true :use-other-user true :body (assoc order-item
                                                                                                    :order-item/recipe-id recipe-id
                                                                                                    :order-item/amount-unit :volume/c)
                                                  :expected-status 400})]
          (is (= status 400))))
      (testing "non production manager can't move items into in progress/completed"
        (are [status]
             (= (:status (ts/test-endpoint :patch (str "/v1/orders/" order-id "/items/" @order-item-id "/status")
                                           {:use-auth?       true :use-other-user true :body {:order-item/status status}
                                            :expected-status 401}))
                401)
          :status/in-progress
          :status/complete))
      (testing "non production manager can't change the status of items that are in progress, completed or canceled"
        (are [status]
             (do
               (ts/test-endpoint :patch (str "/v1/orders/" order-id "/items/" @order-item-id "/status")
                                 {:use-auth? true :expected-status 204 :body {:order-item/status status}})
               (= (:status (ts/test-endpoint :patch (str "/v1/orders/" order-id "/items/" @order-item-id "/status")
                                             {:use-auth?       true :use-other-user true :body {:order-item/status :status/draft}
                                              :expected-status 401}))
                  401))
          :status/canceled
          :status/complete
          :status/in-progress))
      (ts/test-endpoint :patch (str "/v1/orders/" order-id "/items/" @order-item-id "/status")
                        {:use-auth? true :expected-status 204 :body {:order-item/status :status/draft}})
      (testing "non production manager can change the status of items that are not in progress, completed or canceled"
        (are [status]
             (= (:status (ts/test-endpoint :patch (str "/v1/orders/" order-id "/items/" @order-item-id "/status")
                                           {:use-auth?       true :use-other-user true :body {:order-item/status status}
                                            :expected-status 204}))
                204)
          :status/submitted
          :status/draft
          :status/canceled)))
    (testing "retrieve order bom"
      (let [{:keys [status]} (ts/test-endpoint :get (str "/v1/orders/" @order-id "/bom")
                                               {:use-auth? true})]
        (is (= status 200))))
    (testing "can only delete order item that are not in a terminal state"
      (mapv #(let [order-item-id (-> (ts/test-endpoint :post (str "/v1/orders/" @order-id "/items")
                                                       {:use-auth? true :body (assoc order-item :order-item/recipe-id recipe-id)})
                                     :body
                                     :id)]
               (println "TERMINAL" % (terminal? %))
               (ts/test-endpoint :patch (str "/v1/orders/" @order-id "/items/" order-item-id "/status")
                                 {:use-auth? true :expected-status 204 :body {:order-item/status %}})
               (ts/test-endpoint :delete (str "/v1/orders/" @order-id "/items/" order-item-id)
                                 {:use-auth?       true                       :body {:order-item/id order-item-id}
                                  :expected-status (if (terminal? %) 400 204)}))
            status))))

(deftest bulk-status-update-test
  (let [db             (:db/postgres state/system)
        recipe-ids     (doall (repeatedly 3 #(parse-uuid (get-in (ts/test-endpoint :post "/v1/recipes" {:use-auth? true :body recipe :expected-status 201}) [:body :id]))))
        ;; Create an active menu and add the recipe to it
        menu-id        (get-in (ts/test-endpoint :post "/v1/menus" {:use-auth? true :body menu :expected-status 201}) [:body :id])
        menu-item-ids  (mapv #(get-in (ts/test-endpoint :post (str "/v1/menus/" menu-id "/items")
                                                        {:use-auth? true :body {:menu-item/recipe-id %} :expected-status 201})
                                      [:body :id])
                             recipe-ids)
        _              (mapv #(ts/test-endpoint :post (str "/v1/menus/" menu-id "/items/" % "/sizes")
                                                {:use-auth?       true
                                                 :body            {:menu-item-size/amount      1
                                                                   :menu-item-size/amount-unit :volume/pt}
                                                 :expected-status 201})
                             menu-item-ids)
        order-id       (parse-uuid (get-in (ts/test-endpoint :post "/v1/orders" {:use-auth? true :use-other-user true :body order :expected-status 201}) [:body :id]))
        order-item-map (into {} (map #(let [order-item-body (assoc order-item :order-item/recipe-id %)]
                                        [(get-in (ts/test-endpoint :post (str "/v1/orders/" order-id "/items")
                                                                   {:use-auth? true :use-other-user true :body order-item-body :expected-status 201})
                                                 [:body :id])
                                         order-item-body])
                                     recipe-ids))]
    (testing "unsubmitted orders are canceled"
      (order-db/bulk-status-update! db {:status/submitted :status/in-progress
                                        :status/draft     :status/canceled}
                                    (first recipe-ids))
      (let [items (group-by #(= (:order-item/recipe-id %) (first recipe-ids))
                            (apply concat (vals (order-db/find-all-order-items! db order-id))))]
        (is (every? #(= (:order-item/status %) :status/canceled)
                    (get items true)))
        (is (every? #(= (:order-item/status %) :status/draft)
                    (get items false)))))
    (testing "submitted orders are moved to in-progress"
      (dorun
       (map (fn [[item-id body]]
              (when (= (:order-item/recipe-id body) (first recipe-ids))
                (ts/test-endpoint :patch (str "/v1/orders/" order-id "/items/" item-id "/status")
                                  {:use-auth?       true
                                   :body            {:order-item/status :status/submitted}
                                   :expected-status 204})))
            order-item-map))
      (order-db/bulk-status-update! db {:status/submitted :status/in-progress
                                        :status/draft     :status/canceled}
                                    (first recipe-ids))
      (let [items (group-by #(= (:order-item/recipe-id %) (first recipe-ids))
                            (apply concat (vals (order-db/find-all-order-items! db order-id))))]
        (is (every? #(= (:order-item/status %) :status/in-progress)
                    (get items true)))
        (is (every? #(= (:order-item/status %) :status/draft)
                    (get items false)))))
    (testing "completed orders are not moved to in-progress"
      (dorun
       (map (fn [[item-id body]]
              (when (= (:order-item/recipe-id body) (first recipe-ids))
                (ts/test-endpoint :patch (str "/v1/orders/" order-id "/items/" item-id "/status")
                                  {:use-auth?       true
                                   :body            (assoc body
                                                           :order-item/status :status/complete)
                                   :expected-status 204})))
            order-item-map))
      (order-db/bulk-status-update! db {:status/submitted :status/in-progress
                                        :status/draft     :status/canceled}
                                    (first recipe-ids))
      (let [items (group-by #(= (:order-item/recipe-id %) (first recipe-ids))
                            (apply concat (vals (order-db/find-all-order-items! db order-id))))]
        (is (every? #(= (:order-item/status %) :status/complete)
                    (get items true)))
        (is (every? #(= (:order-item/status %) :status/draft)
                    (get items false)))))))

(deftest list-in-progress-items-test
  (let [db         (:db/postgres state/system)
        recipe-ids (doall (repeatedly 2 #(parse-uuid (get-in (ts/test-endpoint :post "/v1/recipes" {:use-auth? true :body recipe}) [:body :id]))))
        order-id   (parse-uuid (get-in (ts/test-endpoint :post "/v1/orders" {:use-auth? true :body order}) [:body :id]))]
    (mapv #(let [order-item-body (assoc order-item :order-item/recipe-id %)]
             [(get-in (ts/test-endpoint :post (str "/v1/orders/" order-id "/items")
                                        {:use-auth? true :body order-item-body})
                      [:body :id])
              order-item-body])
          (apply concat (repeat 2 recipe-ids)))
    (order-db/bulk-status-update! db {:status/draft :status/in-progress}
                                  (first recipe-ids))
    (testing "in progress items are consolidated and returned"
      (let [consolidated-items (:body (ts/test-endpoint :get "/v1/production" {:use-auth? true}))]
        (is (= (count consolidated-items) 1))
        (is (= (parse-uuid (:order-item/recipe-id (first consolidated-items))) (first recipe-ids)))
        (is (= (:order-item/amount (first consolidated-items))
               (->> order-item
                    :order-item/amount
                    (* 2.0))))))))

(deftest complete-orders-for-recipe-test
  (let [db             (:db/postgres state/system)
        recipe-id      (-> (ts/test-endpoint :post "/v1/recipes" {:use-auth? true :body recipe})
                           :body
                           :id
                           parse-uuid)
        order-id       (parse-uuid (get-in (ts/test-endpoint :post "/v1/orders" {:use-auth? true :body order}) [:body :id]))
        order-item-ids (repeatedly 3 #(-> (ts/test-endpoint
                                           :post (str "/v1/orders/" order-id "/items")
                                           {:use-auth? true :body (assoc order-item :order-item/recipe-id recipe-id)})
                                          :body
                                          :id
                                          (parse-uuid)))]
    (mapv #(ts/test-endpoint :patch (str "/v1/orders/" order-id "/items/" % "/status")
                             {:use-auth? true :expected-status 204 :body {:order-item/status :status/in-progress}})
          (take 2 order-item-ids))

    (testing "in progress are moved to complete. Other items are not"
      (is (= (:status (ts/test-endpoint :post (str "/v1/production/" recipe-id) {:use-auth? true :expected-status 204}))
             204))
      (let [updated-items (map (partial order-db/find-order-item-by-id! db) order-item-ids)]
        (are [updated-item expected-status]
             (= (:order-item/status updated-item) expected-status)
          (first updated-items) :status/complete
          (second updated-items) :status/complete
          (last updated-items) :status/draft)))))

(deftest available-quantity-test
  (let [recipe-id         (get-in (ts/test-endpoint :post "/v1/recipes" {:use-auth? true :body recipe}) [:body :id])
        menu-id           (get-in (ts/test-endpoint :post "/v1/menus" {:use-auth? true :body menu}) [:body :id])
        menu-item-id      (get-in (ts/test-endpoint :post (str "/v1/menus/" menu-id "/items")
                                                    {:use-auth? true :body {:menu-item/recipe-id recipe-id}})
                                  [:body :id])
        menu-item-size-id (get-in (ts/test-endpoint :post (str "/v1/menus/" menu-id "/items/" menu-item-id "/sizes")
                                                    {:use-auth? true :body {:menu-item-size/amount             1
                                                                            :menu-item-size/amount-unit        :volume/qt
                                                                            :menu-item-size/available-quantity 2}})
                                  [:body :id])
        order-id-1        (get-in (ts/test-endpoint :post "/v1/orders" {:use-auth? true :use-other-user true :body order}) [:body :id])
        order-id-2        (get-in (ts/test-endpoint :post "/v1/orders" {:use-auth? true :use-other-user true :body order}) [:body :id])
        order-item-body   (assoc order-item :order-item/recipe-id recipe-id
                                 :order-item/menu-item-size-id menu-item-size-id)
        order-item-id-1   (atom nil)
        order-item-id-2   (atom nil)]
    (testing "create order items (draft, no quantity consumed yet)"
      (let [{:keys [status body]} (ts/test-endpoint :post (str "/v1/orders/" order-id-1 "/items")
                                                    {:use-auth? true :use-other-user true :body order-item-body})]
        (reset! order-item-id-1 (:id body))
        (is (= 201 status)))
      (let [{:keys [status body]} (ts/test-endpoint :post (str "/v1/orders/" order-id-2 "/items")
                                                    {:use-auth? true :use-other-user true :body order-item-body})]
        (reset! order-item-id-2 (:id body))
        (is (= 201 status))))
    (testing "submitting first order item decrements available quantity"
      (is (= 204 (:status (ts/test-endpoint :patch (str "/v1/orders/" order-id-1 "/items/" @order-item-id-1 "/status")
                                            {:use-auth? true :use-other-user true :expected-status 204 :body {:order-item/status :status/submitted}})))))
    (testing "submitting second order item decrements available quantity to zero"
      (is (= 204 (:status (ts/test-endpoint :patch (str "/v1/orders/" order-id-2 "/items/" @order-item-id-2 "/status")
                                            {:use-auth? true :use-other-user true :expected-status 204 :body {:order-item/status :status/submitted}})))))
    (testing "third order item cannot be submitted when available quantity is exhausted"
      (let [order-id-3      (get-in (ts/test-endpoint :post "/v1/orders" {:use-auth? true :use-other-user true :body order}) [:body :id])
            order-item-id-3 (get-in (ts/test-endpoint :post (str "/v1/orders/" order-id-3 "/items")
                                                      {:use-auth? true :use-other-user true :body order-item-body})
                                    [:body :id])]
        (is (= 400 (:status (ts/test-endpoint :patch (str "/v1/orders/" order-id-3 "/items/" order-item-id-3 "/status")
                                              {:use-auth? true :use-other-user true :expected-status 400 :body {:order-item/status :status/submitted}}))))))
    (testing "reverting a submitted order item to draft restores available quantity"
      (is (= 204 (:status (ts/test-endpoint :patch (str "/v1/orders/" order-id-1 "/items/" @order-item-id-1 "/status")
                                            {:use-auth? true :use-other-user true :expected-status 204 :body {:order-item/status :status/draft}}))))
      (let [order-id-4      (get-in (ts/test-endpoint :post "/v1/orders" {:use-auth? true :use-other-user true :body order}) [:body :id])
            order-item-id-4 (get-in (ts/test-endpoint :post (str "/v1/orders/" order-id-4 "/items")
                                                      {:use-auth? true :use-other-user true :body order-item-body})
                                    [:body :id])]
        (is (= 204 (:status (ts/test-endpoint :patch (str "/v1/orders/" order-id-4 "/items/" order-item-id-4 "/status")
                                              {:use-auth? true :use-other-user true :expected-status 204 :body {:order-item/status :status/submitted}}))))))
    (testing "deleting a submitted order item restores available quantity"
      (let [order-id-5      (get-in (ts/test-endpoint :post "/v1/orders" {:use-auth? true :use-other-user true :body order}) [:body :id])
            order-item-id-5 (get-in (ts/test-endpoint :post (str "/v1/orders/" order-id-5 "/items")
                                                      {:use-auth? true :use-other-user true :body order-item-body})
                                    [:body :id])]
        (is (= 400 (:status (ts/test-endpoint :patch (str "/v1/orders/" order-id-5 "/items/" order-item-id-5 "/status")
                                              {:use-auth? true :use-other-user true :expected-status 400 :body {:order-item/status :status/submitted}})))
            "exhausted before delete")
        (ts/test-endpoint :delete (str "/v1/orders/" order-id-2 "/items/" @order-item-id-2)
                          {:use-auth? true :use-other-user true})
        (is (= 204 (:status (ts/test-endpoint :patch (str "/v1/orders/" order-id-5 "/items/" order-item-id-5 "/status")
                                              {:use-auth? true :use-other-user true :expected-status 204 :body {:order-item/status :status/submitted}})))
            "succeeds after deleting a submitted item")))
    (testing "negative available-quantity means unlimited; submission always succeeds"
      (let [unlimited-size-id   (get-in (ts/test-endpoint :post (str "/v1/menus/" menu-id "/items/" menu-item-id "/sizes")
                                                          {:use-auth? true :body {:menu-item-size/amount             1
                                                                                  :menu-item-size/amount-unit        :volume/qt
                                                                                  :menu-item-size/available-quantity -1}})
                                        [:body :id])
            unlimited-item-body (assoc order-item :order-item/recipe-id recipe-id
                                       :order-item/menu-item-size-id unlimited-size-id)]
        (dotimes [_ 5]
          (let [oid (get-in (ts/test-endpoint :post "/v1/orders" {:use-auth? true :use-other-user true :body order}) [:body :id])
                iid (get-in (ts/test-endpoint :post (str "/v1/orders/" oid "/items")
                                              {:use-auth? true :use-other-user true :body unlimited-item-body})
                            [:body :id])]
            (is (= 204 (:status (ts/test-endpoint :patch (str "/v1/orders/" oid "/items/" iid "/status")
                                                  {:use-auth? true :use-other-user true :expected-status 204 :body {:order-item/status :status/submitted}}))))))))))

