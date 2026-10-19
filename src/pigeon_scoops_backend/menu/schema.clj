(ns pigeon-scoops-backend.menu.schema
  (:require [pigeon-scoops-backend.units.common :refer [all-units]]))

(def durations
  [:duration/day
   :duration/week
   :duration/month])

(def menu-item-size
  [:map
   [:menu-item-size/id :uuid]
   [:menu-item-size/menu-item-id :uuid]
   [:menu-item-size/amount {:min 0} :double]
   [:menu-item-size/amount-unit (vec (concat [:enum] all-units))]
   [:menu-item-size/available-quantity {:optional true} :int]])

(def menu-item
  [:map
   [:menu-item/id :uuid]
   [:menu-item/menu-id :uuid]
   [:menu-item/recipe-id :uuid]
   [:menu-item/sizes [:vector menu-item-size]]])

(def menu
  [:map
   [:menu/id :uuid]
   [:menu/name :string]
   [:menu/repeats {:optional true} :boolean]
   [:menu/active {:optional true} :boolean]
   [:menu/duration {:min 0} :int]
   [:menu/duration-type (vec (concat [:enum] durations))]
   [:menu/items {:optional true} [:vector menu-item]]
   [:menu/end-time {:optional true} :time/instant]])
