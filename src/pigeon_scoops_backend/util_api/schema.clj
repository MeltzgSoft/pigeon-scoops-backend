(ns pigeon-scoops-backend.util-api.schema
  (:require [pigeon-scoops-backend.account.schema :as account-schema]
            [pigeon-scoops-backend.grocery.schema :as grocery-schema]
            [pigeon-scoops-backend.menu.schema :as menu-schema]
            [pigeon-scoops-backend.units.schema :as unit-schema]
            [pigeon-scoops-backend.user-order.schema :as order-schema]))

(def constants
  [:map
   [:constants/unit-types [:vector unit-schema/all-units-enum]]
   [:constants/departments [:vector grocery-schema/departments-enum]]
   [:constants/roles [:vector account-schema/roles-enum]]
   [:constants/order-statuses [:vector order-schema/status-enum]]
   [:constants/menu-durations [:vector menu-schema/duration-enum]]])
