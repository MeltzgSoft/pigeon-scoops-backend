(ns pigeon-scoops-backend.user-order.schema
  (:require [pigeon-scoops-backend.units.schema :as unit-schema]))

(def status
  [:status/draft
   :status/submitted
   :status/in-progress
   :status/complete
   :status/canceled])

(def status-enum
  (vec (concat [:enum] status)))

(defn terminal? [s]
  (#{:status/complete :status/in-progress} s))

(def order-item
  [:map
   [:order-item/id {:optional true} :uuid]
   [:order-item/recipe-id :uuid]
   [:order-item/order-id {:optional true} :uuid]
   [:order-item/status status-enum]
   [:order-item/amount {:optional true} {:min 0} :double]
   [:order-item/amount-unit {:optional true} unit-schema/all-units-enum]
   [:order-item/menu-item-size-id {:optional true} :uuid]
   [:order-item/menu-item-size-quantity {:optional true} {:min 0} :int]])

(def order
  [:map
   [:user-order/id :uuid]
   [:user-order/note :string]
   [:user-order/user-id :string]
   [:user-order/status status-enum]
   [:user-order/created-at :time/instant]
   [:user-order/items {:optional true} [:vector order-item]]])
