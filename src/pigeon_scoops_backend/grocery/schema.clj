(ns pigeon-scoops-backend.grocery.schema
  (:require [pigeon-scoops-backend.units.schema :as unit-schema]))

(def departments
  [:department/dairy
   :department/produce
   :department/grocery])

(def departments-enum
  (vec (concat [:enum] departments)))

(def grocery-unit
  [:map
   [:grocery-unit/id :uuid]
   [:grocery-unit/grocery-id :uuid]
   [:grocery-unit/source :string]
   [:grocery-unit/unit-cost :double]
   [:grocery-unit/unit-mass {:optional true} [:double {:min 0}]]
   [:grocery-unit/unit-mass-type {:optional true} unit-schema/mass-enum]
   [:grocery-unit/unit-volume {:optional true} [:double {:min 0}]]
   [:grocery-unit/unit-volume-type {:optional true} unit-schema/volume-enum]
   [:grocery-unit/unit-common {:optional true} [:double {:min 0}]]
   [:grocery-unit/unit-common-type {:optional true} unit-schema/common-enum]
   [:grocery-unit/quantity {:optional true} [:int {:min 0}]]])

(def grocery
  [:map
   [:grocery/id :uuid]
   [:grocery/name :string]
   [:grocery/department departments-enum]
   [:grocery/units {:optional true} [:vector grocery-unit]]
   [:grocery/required-amount {:optional true} [:double {:min 0}]]
   [:grocery/required-unit {:optional true} unit-schema/all-units-enum]
   [:grocery/purchase-amount {:optional true} [:double {:min 0}]]
   [:grocery/purchase-unit {:optional true} unit-schema/all-units-enum]
   [:grocery/waste-ratio {:optional true} [:double {:min 0 :max 1}]]])

