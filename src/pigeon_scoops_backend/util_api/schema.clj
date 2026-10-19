(ns pigeon-scoops-backend.util-api.schema
  (:require [pigeon-scoops-backend.auth :refer [roles]]
            [pigeon-scoops-backend.grocery.schema :refer [departments]]
            [pigeon-scoops-backend.menu.schema :refer [durations]]
            [pigeon-scoops-backend.units.common :as common]
            [pigeon-scoops-backend.user-order.schema :refer [status]]))

(def constants
  [:map
   [:constants/unit-types [:vector (vec (concat [:enum] common/all-units))]]
   [:constants/departments [:vector (vec (concat [:enum] departments))]]
   [:constants/roles [:vector (vec (concat [:enum] roles))]]
   [:constants/order-statuses [:vector (vec (concat [:enum] status))]]
   [:constants/menu-durations [:vector (vec (concat [:enum] durations))]]])
