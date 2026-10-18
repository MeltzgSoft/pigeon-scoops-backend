(ns pigeon-scoops-backend.menu.schema
  (:require [spec-tools.data-spec :as ds]))

(def durations
  #{:duration/day
    :duration/week
    :duration/month})

(def menu-item-size
  {:menu-item-size/id                        uuid?
   :menu-item-size/menu-item-id              uuid?
   :menu-item-size/amount                    number?
   :menu-item-size/amount-unit               keyword?
   (ds/opt :menu-item-size/available-quantity) integer?})

(def menu-item
  {:menu-item/id        uuid?
   :menu-item/menu-id   uuid?
   :menu-item/recipe-id uuid?
   :menu-item/sizes     [menu-item-size]})

(def menu
  {:menu/id                uuid?
   :menu/name              string?
   (ds/opt :menu/repeats)  boolean?
   (ds/opt :menu/active)   boolean?
   :menu/duration          number?
   :menu/duration-type     keyword?
   (ds/opt :menu/items)    [menu-item]
   (ds/opt :menu/end-time) inst?})
