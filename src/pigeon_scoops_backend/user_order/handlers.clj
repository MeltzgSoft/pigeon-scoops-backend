(ns pigeon-scoops-backend.user-order.handlers
  (:require [next.jdbc :as jdbc]
            [pigeon-scoops-backend.grocery.db :refer [find-grocery-by-id!]]
            [pigeon-scoops-backend.grocery.transforms :refer [grocery-for-amount]]
            [pigeon-scoops-backend.menu.db :as menu-db]
            [pigeon-scoops-backend.recipe.db :as recipe-db]
            [pigeon-scoops-backend.responses :as responses]
            [pigeon-scoops-backend.units.common :as units]
            [pigeon-scoops-backend.user-order.db :as order-db]
            [pigeon-scoops-backend.user-order.responses :refer [terminal?]]
            [pigeon-scoops-backend.utils :refer [with-connection! production-manager? combine-amounts]]
            [ring.util.response :as rr])
  (:import (java.util UUID)))

(defn list-all-orders! [db]
  (fn [request]
    (let [production-manager? (production-manager? request)
          {admin-request? :admin detailed? :detailed} (-> request :parameters :query)
          uid (when-not (and admin-request?
                             production-manager?)
                (-> request :claims :sub))]
      (rr/response (vec (order-db/find-all-orders! db uid detailed?))))))

(defn create-order! [db]
  (fn [request]
    (let [order-id (UUID/randomUUID)
          uid (-> request :claims :sub)
          order (-> request :parameters :body)]
      (order-db/insert-order! db (assoc order
                                        :user-order/id order-id
                                        :user-order/user-id uid))
      (rr/created (str responses/base-url "/orders/" order-id)
                  {:id order-id}))))

(defn retrieve-order! [db]
  (fn [request]
    (let [order-id (-> request :parameters :path :order-id)
          order (order-db/find-order-by-id! db order-id)]
      (if order
        (rr/response (update order :user-order/items vec))
        (rr/not-found {:type    "order-not-found"
                       :message "order not found"
                       :data    (str "order-id " order-id)})))))

(defn update-order! [db]
  (fn [request]
    (let [order-id (-> request :parameters :path :order-id)
          order (-> request :parameters :body)
          successful? (order-db/update-order! db (assoc order :user-order/id order-id))]
      (if successful?
        (rr/status 204)
        (rr/not-found {:type    "order-not-found"
                       :message "order not found"
                       :data    (str "order-id " order-id)})))))

(defn delete-order! [db]
  (fn [request]
    (let [order-id (-> request :parameters :path :order-id)
          order (order-db/find-order-by-id! db order-id)]
      (cond
        (not order) (rr/not-found {:type    "order-not-found"
                                   :message "order not found"
                                   :data    (str "order-id " order-id)})
        (pos? (count (:user-order/items order))) (rr/bad-request {:type    "non-empty-order"
                                                                  :message "Order can only be deleted if they have no items"
                                                                  :data    (str "order-id " order-id)})
        (order-db/delete-order! db order-id) (rr/status 204)
        :else
        (rr/status 500)))))

(defn create-order-item! [db]
  (fn [request]
    (with-connection!
      db
      (fn [db]
        (let [order-id (-> request :parameters :path :order-id)
              production-manager? (production-manager? request)
              {:order-item/keys [recipe-id amount amount-unit] :as order-item} (-> request :parameters :body)
              order-item-id (UUID/randomUUID)
              active-items (get
                            (group-by
                             :menu-item/recipe-id
                             (menu-db/find-active-menu-items! db))
                            recipe-id)
              active-item-sizes (when active-items (apply (partial menu-db/find-menu-item-sizes-by-items! db)
                                                          (map :menu-item/id active-items)))]
          (cond
            (not (or production-manager? (seq active-items)))
            (rr/bad-request {:type    "recipe-not-in-active-menu"
                             :message "recipe is not in an active menu"
                             :data    (str "recipe-id " recipe-id)})
            (and (not production-manager?) (not-any? #(zero?
                                                       (first
                                                        (units/reduce-amounts mod amount amount-unit
                                                                              (:menu-item-size/amount %)
                                                                              (:menu-item-size/amount-unit %))))
                                                     active-item-sizes))
            (rr/bad-request {:type    "no-valid-size-order-amount"
                             :message "order amount cannot be made from any active item size"
                             :data    active-item-sizes})
            :else
            (do
              (order-db/insert-order-item! db (assoc order-item
                                                     :order-item/status :status/draft
                                                     :order-item/order-id order-id
                                                     :order-item/id order-item-id))
              (rr/created (str responses/base-url "/orders/" order-id)
                          {:id order-item-id}))))))))

(defn update-order-item! [db]
  (fn [request]
    (with-connection!
      db
      (fn [db]
        (let [{:keys [order-id order-item-id]} (-> request
                                                   :parameters
                                                   :path
                                                   (select-keys [:order-id :order-item-id]))
              production-manager? (production-manager? request)
              {:order-item/keys [recipe-id amount amount-unit] :as order-item} (-> request :parameters :body)
              order-item (assoc order-item
                                :order-item/id order-item-id
                                :order-item/order-id order-id)
              curr-item-status (->> order-item-id
                                    (order-db/find-order-item-by-id! db)
                                    :order-item/status)
              active-items (get
                            (group-by
                             :menu-item/recipe-id
                             (menu-db/find-active-menu-items! db))
                            recipe-id)
              active-item-sizes (when active-items (apply (partial menu-db/find-menu-item-sizes-by-items! db)
                                                          (map :menu-item/id active-items)))]
          (cond
            (not= curr-item-status :status/draft)
            (rr/bad-request {:type "recipe-not-editable"
                             :message "only draft order items can be changed"
                             :data (str "status " curr-item-status)})
            (not (or production-manager? (seq active-items)))
            (rr/bad-request {:type    "recipe-not-in-active-menu"
                             :message "recipe is not in an active menu"
                             :data    (str "recipe-id " recipe-id)})
            (not (or production-manager?
                     (some #(zero?
                             (first
                              (units/reduce-amounts mod amount amount-unit
                                                    (:menu-item-size/amount %)
                                                    (:menu-item-size/amount-unit %))))
                           active-item-sizes)))
            (rr/bad-request {:type    "no-valid-size-order-amount"
                             :message "order amount cannot be made from any active item size"
                             :data    active-item-sizes})
            :else
            (if (order-db/update-order-item! db order-item)
              (rr/status 204)
              (rr/bad-request order-item))))))))

(defn update-order-item-status! [db]
  (fn [request]
    (with-connection!
      db
      (fn [db]
        (let [{:keys [order-id order-item-id]} (-> request
                                                   :parameters
                                                   :path
                                                   (select-keys [:order-id :order-item-id]))
              new-status (-> request
                             :parameters
                             :body
                             :order-item/status)
              production-manager? (production-manager? request)
              curr-item (order-db/find-order-item-by-id! db order-item-id)
              curr-item-status (:order-item/status curr-item)
              patch {:order-item/id order-item-id
                     :order-item/order-id order-id
                     :order-item/status new-status}]
          (cond
            (and (not production-manager?)
                 (not (#{:status/draft :status/submitted :status/canceled} new-status)))
            (-> (rr/response {:message (str "only s can set the status to " new-status)
                              :data    patch
                              :type    :authorization-required})
                (rr/status 401))
            (and (not production-manager?)
                 (#{:status/in-progress :status/complete :status/canceled} curr-item-status))
            (-> (rr/response {:message (str "only s can change the status of items in " curr-item-status)
                              :data    patch
                              :type    :authorization-required})
                (rr/status 401))
            :else
            (let [size-id (:order-item/menu-item-size-id curr-item)
                  size-qty (some-> (:order-item/menu-item-size-quantity curr-item) long)]
              (jdbc/with-transaction+options [conn db]
                (cond
                  (and (= new-status :status/submitted)
                       size-id size-qty
                       (not (menu-db/decrement-menu-item-size-available-quantity! conn size-id size-qty)))
                  (rr/bad-request {:type    "insufficient-quantity"
                                   :message "Not enough quantity available for this size"
                                   :data    {:menu-item-size-id size-id}})
                  :else
                  (do
                    (when (and (= curr-item-status :status/submitted)
                               (= new-status :status/draft)
                               size-id size-qty)
                      (menu-db/increment-menu-item-size-available-quantity! conn size-id size-qty))
                    (if (order-db/update-order-item! conn patch)
                      (rr/status 204)
                      (rr/bad-request patch))))))))))))

(defn delete-order-item! [db]
  (fn [request]
    (with-connection!
      db
      (fn [db]
        (let [{:keys [order-id order-item-id]} (-> request
                                                   :parameters
                                                   :path
                                                   (select-keys [:order-id :order-item-id]))
              curr-item (order-db/find-order-item-by-id! db order-item-id)
              order-item-status (:order-item/status curr-item)]
          (cond
            (terminal? order-item-status)
            (rr/bad-request {:type    "non-deletable-status"
                             :message (str "Cannot delete an order item in a terminal state. " order-item-status " is not terminal")
                             :data    order-item-status})
            :else
            (jdbc/with-transaction+options [conn db]
              (if (order-db/delete-order-item! conn {:id order-item-id :order-id order-id})
                (do
                  (when (and (= order-item-status :status/submitted)
                             (:order-item/menu-item-size-id curr-item)
                             (:order-item/menu-item-size-quantity curr-item))
                    (menu-db/increment-menu-item-size-available-quantity!
                     conn
                     (:order-item/menu-item-size-id curr-item)
                     (long (:order-item/menu-item-size-quantity curr-item))))
                  (rr/status 204))
                (rr/bad-request (-> request :parameters :body))))))))))

(defn retrieve-order-bom! [db]
  (fn [request]
    (with-connection!
      db
      (fn [db]
        (let [order-id (-> request :parameters :path :order-id)
              {:user-order/keys [items]} (order-db/find-order-by-id! db order-id)
              grocery-bom (->> items
                               (mapcat #(recipe-db/ingredient-bom! db {:recipe/id          (:order-item/recipe-id %)
                                                                       :recipe/amount      (:order-item/amount %)
                                                                       :recipe/amount-unit (:order-item/amount-unit %)}))
                               (#(combine-amounts %
                                                  :ingredient/amount
                                                  :ingredient/amount-unit
                                                  :ingredient/ingredient-recipe-id
                                                  :ingredient/ingredient-grocery-id))
                               (map #(update (grocery-for-amount
                                              (find-grocery-by-id! db (:ingredient/ingredient-grocery-id %))
                                              (:ingredient/amount %)
                                              (:ingredient/amount-unit %))
                                             :grocery/units
                                             vec)))]
          (rr/response (vec grocery-bom)))))))

(defn list-in-progress-items! [db]
  (fn [request]
    (let [separate-sizes? (-> request :parameters :query :separate-sizes)]
      (-> (order-db/find-all-items-by-status! db :status/in-progress)
          (combine-amounts :order-item/amount
                           :order-item/amount-unit
                           :order-item/recipe-id
                           (when separate-sizes?
                             :order-item/menu-item-size-id))
          ((partial map #(apply dissoc % (concat [:order-item/id :order-item/order-id]
                                                 (when-not separate-sizes?
                                                   [:order-item/menu-item-size-id
                                                    :order-item/menu-item-size-quantity])))))
          (rr/response)))))

(defn complete-orders-for-recipe! [db]
  (fn [request]
    (let [recipe-id (-> request :parameters :path :recipe-id)]
      (order-db/bulk-status-update! db {:status/in-progress :status/complete} recipe-id))
    (rr/status 204)))
