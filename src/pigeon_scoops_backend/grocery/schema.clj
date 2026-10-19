(ns pigeon-scoops-backend.grocery.schema
  (:require [pigeon-scoops-backend.units.common :as common]
            [pigeon-scoops-backend.units.mass :as mass]
            [pigeon-scoops-backend.units.volume :as volume]))

(def departments
  [:department/dairy
   :department/produce
   :department/grocery])

(def grocery-unit
  [:map
   [:grocery-unit/id :uuid]
   [:grocery-unit/grocery-id :uuid]
   [:grocery-unit/source :string]
   [:grocery-unit/unit-cost :double]
   [:grocery-unit/unit-mass {:optional true :min 0} :double]
   [:grocery-unit/unit-mass-type {:optional true} (vec (concat [:enum] (keys mass/conversion-map)))]
   [:grocery-unit/unit-volume {:optional true :min 0} :double]
   [:grocery-unit/unit-volume-type {:optional true} (vec (concat [:enum] (keys volume/conversion-map)))]
   [:grocery-unit/unit-common {:optional true :min 0} :double]
   [:grocery-unit/unit-common-type {:optional true} (vec (concat [:enum] common/other-units))]
   [:grocery-unit/quantity {:optional true :min 0} :int]])

(def grocery
  [:map
   [:grocery/id :uuid]
   [:grocery/name :string]
   [:grocery/department (vec (concat [:enum] departments))]
   [:grocery/units {:optional true} [:vector grocery-unit]]
   [:grocery/required-amount {:optional true :min 0} :double]
   [:grocery/required-unit {:optional true} (vec (concat [:enum] common/all-units))]
   [:grocery/purchase-amount {:optional true :min 0} :int]
   [:grocery/purchase-unit {:optional true} (vec (concat [:enum] common/all-units))]
   [:grocery/waste-ratio {:optional true :min 0 :max 1} :double]])
