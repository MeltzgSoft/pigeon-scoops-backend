(ns pigeon-scoops-backend.user-order.schema
  (:require [pigeon-scoops-backend.units.common :as common]))

(def status
  [:status/draft
   :status/submitted
   :status/in-progress
   :status/complete
   :status/canceled])

(defn terminal? [s]
  (#{:status/complete :status/in-progress} s))

(def order-item
  [:map
   [:order-item/id {:optional true} :uuid]
   [:order-item/recipe-id :uuid]
   [:order-item/order-id {:optional true} :uuid]
   [:order-item/status (vec (concat [:enum] status))]
   [:order-item/amount {:optional true} {:min 0} :double]
   [:order-item/amount-unit {:optional true} (vec (concat [:enum] common/all-units))]
   [:order-item/menu-item-size-id {:optional true} :uuid]
   [:order-item/menu-item-size-quantity {:optional true} {:min 0} :int]])

(def order
  [:map
   [:user-order/id :uuid]
   [:user-order/note :string]
   [:user-order/user-id :string]
   [:user-order/status (vec (concat [:enum] status))]
   [:user-order/created-at :time/instant]
   [:user-order/items {:optional true} [:vector order-item]]])
