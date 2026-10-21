(ns pigeon-scoops-backend.grocery.schema
  (:require [pigeon-scoops-backend.schema :refer [sub-schema]]
            [pigeon-scoops-backend.units.schema :as unit-schema]))

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
   [:grocery-unit/unit-mass {:optional true :min 0} :double]
   [:grocery-unit/unit-mass-type {:optional true} unit-schema/mass-enum]
   [:grocery-unit/unit-volume {:optional true :min 0} :double]
   [:grocery-unit/unit-volume-type {:optional true} unit-schema/volume-enum]
   [:grocery-unit/unit-common {:optional true :min 0} :double]
   [:grocery-unit/unit-common-type {:optional true} unit-schema/common-enum]
   [:grocery-unit/quantity {:optional true :min 0} :int]])

(def grocery
  [:map
   [:grocery/id :uuid]
   [:grocery/name :string]
   [:grocery/department departments-enum]
   [:grocery/units {:optional true} [:vector grocery-unit]]
   [:grocery/required-amount {:optional true :min 0} :double]
   [:grocery/required-unit {:optional true} unit-schema/all-units-enum]
   [:grocery/purchase-amount {:optional true :min 0} :int]
   [:grocery/purchase-unit {:optional true} unit-schema/all-units-enum]
   [:grocery/waste-ratio {:optional true :min 0 :max 1} :double]])

(def create-grocery
  (sub-schema grocery [:grocery/name
                       :grocery/department]))

(def create-grocery-unit
  (sub-schema grocery-unit [:grocery-unit/source
                            :grocery-unit/unit-cost
                            :grocery-unit/unit-common
                            :grocery-unit/unit-common-type
                            :grocery-unit/unit-mass
                            :grocery-unit/unit-mass-type
                            :grocery-unit/unit-volume
                            :grocery-unit/unit-volume-type]))

