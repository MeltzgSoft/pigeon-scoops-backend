(ns pigeon-scoops-backend.user-order.schema
  (:require [clojure.spec.alpha :as s]
            [pigeon-scoops-backend.units.common :as common]
            [pigeon-scoops-backend.units.mass :as mass]
            [pigeon-scoops-backend.units.volume :as volume]
            [spec-tools.data-spec :as ds]))

(def status
  [:status/draft
   :status/submitted
   :status/in-progress
   :status/complete
   :status/canceled])

(defn terminal? [s]
  (#{:status/complete :status/in-progress} s))

(def order-item
  {(ds/opt :order-item/id)                   uuid?
   :order-item/recipe-id            uuid?
   (ds/opt :order-item/order-id)             uuid?
   :order-item/status               (s/and keyword? (set status))
   (ds/opt :order-item/amount)      pos?
   (ds/opt :order-item/amount-unit) (s/and keyword?
                                           (set (concat common/other-units
                                                        (keys mass/conversion-map)
                                                        (keys volume/conversion-map))))
   (ds/opt :order-item/menu-item-size-id) uuid?
   (ds/opt :order-item/menu-item-size-quantity) pos?})

(def order
  {:user-order/id             uuid?
   :user-order/note           string?
   :user-order/user-id        string?
   :user-order/status         (s/and keyword? (set status))
   :user-order/created-at     inst?
   (ds/opt :user-order/items) [order-item]})
