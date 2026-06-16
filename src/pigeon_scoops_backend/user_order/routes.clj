(ns pigeon-scoops-backend.user-order.routes
  (:require [clojure.spec.alpha :as s]
            [pigeon-scoops-backend.grocery.responses :as grocery-responses]
            [pigeon-scoops-backend.middleware :as mw]
            [pigeon-scoops-backend.units.common :as common]
            [pigeon-scoops-backend.units.mass :as mass]
            [pigeon-scoops-backend.units.volume :as volume]
            [pigeon-scoops-backend.user-order.db :as order-db]
            [pigeon-scoops-backend.user-order.handlers :as order]
            [pigeon-scoops-backend.user-order.responses :as responses]
            [pigeon-scoops-backend.utils :refer [production-manager?]]
            [spec-tools.data-spec :as ds]))

(defn routes [{db :jdbc-url}]
  [["/production" {:openapi {:tags ["production"]}
                   :middleware [[(mw/wrap-with-permission :manage/production)]]}
    ["" {:get {:handler (order/list-in-progress-items! db)
               :parameters {:query {(ds/opt :separate-sizes) boolean?}}
               :response [responses/order-item]
               :summary "list order items for in-progress orders"}}]
    ["/:recipe-id" {:post {:handler (order/complete-orders-for-recipe! db)
                           :parameters {:path {:recipe-id uuid?}}
                           :responses {204 {:body nil?}}
                           :summary "mark all in progress items for this recipe as complete"}}]]
   ["/orders" {:openapi    {:tags ["orders"]}}
    ["" {:get  {:handler   (order/list-all-orders! db)
                :parameters {:query {(ds/opt :admin) boolean?
                                     (ds/opt :detailed) boolean?}}
                :responses {200 {:body [responses/order]}}
                :summary "list of orders"}
         :post {:handler    (order/create-order! db)
                :middleware [[(mw/wrap-with-permission :create/order)]]
                :parameters {:body {:user-order/note string?}}
                :responses  {201 {:body {:id uuid?}}}}}]
    ["/:order-id" {:parameters {:path {:order-id uuid?}}
                   :middleware [[(mw/wrap-owner :order-id :user-order order-db/find-order-by-id! production-manager?) db]]}
     ["" {:get    {:handler   (order/retrieve-order! db)
                   :responses {200 {:body responses/order}}
                   :summary   "Retrieve order"}
          :put    {:handler    (order/update-order! db)
                   :middleware [[(mw/wrap-with-permission :edit/order)]]
                   :parameters {:body {:user-order/note   string?}}
                   :responses  {204 {:body nil?}}
                   :summary    "Update order"}
          :delete {:handler    (order/delete-order! db)
                   :responses  {204 {:body nil?}}
                   :summary    "delete order"}}]
     ["/bom" {:get {:handler   (order/retrieve-order-bom! db)
                    :responses {200 {:body [grocery-responses/grocery]}}
                    :summary   "Retrieve order bom"}}]
     ["/items" {:middleware [[(mw/wrap-with-permission :edit/order)]]}
      ["" {:post   {:handler    (order/create-order-item! db)
                    :parameters {:body {:order-item/recipe-id   uuid?
                                        :order-item/amount      pos?
                                        :order-item/amount-unit (s/and keyword? (set (concat common/other-units
                                                                                             (keys mass/conversion-map)
                                                                                             (keys volume/conversion-map))))
                                        (ds/opt :order-item/menu-item-size-id) (s/nilable uuid?)}}
                    :responses  {201 {:body {:id uuid?}}}
                    :summary    "Create order-item"}}]
      ["/:order-item-id"
       {:parameters {:path {:order-item-id uuid?}}}
       ["" {:patch    {:handler    (order/update-order-item! db)
                       :parameters {:body {:order-item/recipe-id   uuid?
                                           :order-item/amount      pos?
                                           :order-item/amount-unit (s/and keyword? (set (concat common/other-units
                                                                                                (keys mass/conversion-map)
                                                                                                (keys volume/conversion-map))))
                                           (ds/opt :order-item/menu-item-size-id) (s/nilable uuid?)}}
                       :responses  {204 {:body nil?}}
                       :summary    "Update order-item"}
            :delete {:handler    (order/delete-order-item! db)
                     :responses  {204 {:body nil?}}
                     :summary    "delete order-item"}}]
       ["/status" {:patch    {:handler    (order/update-order-item-status! db)
                              :parameters {:body {:order-item/status   (s/and keyword? (set responses/status))}}
                              :responses  {204 {:body nil?}}
                              :summary    "Update order-item stus"}}]]]]]])
