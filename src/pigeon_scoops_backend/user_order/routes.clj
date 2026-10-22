(ns pigeon-scoops-backend.user-order.routes
  (:require [pigeon-scoops-backend.schema :refer [create-response sub-schema]]
            [pigeon-scoops-backend.grocery.schema :refer [grocery]]
            [pigeon-scoops-backend.middleware :as mw]
            [pigeon-scoops-backend.user-order.db :as order-db]
            [pigeon-scoops-backend.user-order.handlers :as order]
            [pigeon-scoops-backend.user-order.schema :as schema]
            [pigeon-scoops-backend.utils :refer [production-manager?]]))

(def create-order
  (sub-schema schema/order [:user-order/note]))

(def create-order-item
  (sub-schema schema/order-item [:order-item/recipe-id
                                 :order-item/amount
                                 :order-item/amount-unit
                                 :order-item/menu-item-size-id]))

(defn routes [{db :jdbc-url}]
  [["/production" {:openapi    {:tags ["production"]}
                   :middleware [[(mw/wrap-with-permission :manage/production)]]}
    ["" {:get {:handler    (order/list-in-progress-items! db)
               :parameters {:query [:map [:separate-sizes {:optional true} :boolean]]}
               :responses  {200 {:body [:vector schema/order-item]}}
               :summary    "list order items for in-progress orders"}}]
    ["/:recipe-id" {:post {:handler    (order/complete-orders-for-recipe! db)
                           :parameters {:path [:map [:recipe-id :uuid]]}
                           :responses  {204 {:body :nil}}
                           :summary    "mark all in progress items for this recipe as complete"}}]]
   ["/orders" {:openapi {:tags ["orders"]}}
    ["" {:get  {:handler    (order/list-all-orders! db)
                :parameters {:query [:map
                                     [:admin {:optional true} :boolean]
                                     [:detailed {:optional true} :boolean]]}
                :responses  {200 {:body [:vector schema/order]}}
                :summary    "list of orders"}
         :post {:handler    (order/create-order! db)
                :middleware [[(mw/wrap-with-permission :create/order)]]
                :parameters {:body create-order}
                :responses  {201 {:body create-response}}
                :summary    "create an order"}}]
    ["/:order-id" {:parameters {:path [:map [:order-id :uuid]]}
                   :middleware [[(mw/wrap-owner :order-id :user-order order-db/find-order-by-id! production-manager?) db]]}
     ["" {:get    {:handler   (order/retrieve-order! db)
                   :responses {200 {:body schema/order}}
                   :summary   "Retrieve order"}
          :put    {:handler    (order/update-order! db)
                   :middleware [[(mw/wrap-with-permission :edit/order)]]
                   :parameters {:body create-order}
                   :responses  {204 {:body :nil}}
                   :summary    "Update order"}
          :delete {:handler   (order/delete-order! db)
                   :responses {204 {:body :nil}}
                   :summary   "delete order"}}]
     ["/bom" {:get {:handler   (order/retrieve-order-bom! db)
                    :responses {200 {:body [:vector grocery]}}
                    :summary   "Retrieve order bom"}}]
     ["/items" {:middleware [[(mw/wrap-with-permission :edit/order)]]}
      ["" {:post {:handler    (order/create-order-item! db)
                  :parameters {:body create-order-item}
                  :responses  {201 {:body create-response}}
                  :summary    "Create order-item"}}]
      ["/:order-item-id"
       {:parameters {:path [:map [:order-item-id :uuid]]}}
       ["" {:patch  {:handler    (order/update-order-item! db)
                     :parameters {:body create-order-item}
                     :responses  {204 {:body :nil}}
                     :summary    "Update order-item"}
            :delete {:handler   (order/delete-order-item! db)
                     :responses {204 {:body :nil}}
                     :summary   "delete order-item"}}]
       ["/status" {:patch {:handler    (order/update-order-item-status! db)
                           :parameters {:body [:map [:order-item/status schema/status-enum]]}
                           :responses  {204 {:body :nil}}
                           :summary    "Update order-item status"}}]]]]]])
