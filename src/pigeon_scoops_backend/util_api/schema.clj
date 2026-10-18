(ns pigeon-scoops-backend.util-api.schema
  (:require [clojure.spec.alpha :as s]
            [pigeon-scoops-backend.auth :refer [roles]]
            [pigeon-scoops-backend.grocery.schema :refer [departments]]
            [pigeon-scoops-backend.menu.schema :refer [durations]]
            [pigeon-scoops-backend.units.common :as common]
            [pigeon-scoops-backend.units.mass :as mass]
            [pigeon-scoops-backend.units.volume :as volume]
            [pigeon-scoops-backend.user-order.schema :refer [status]]))

(def constants
  {:constants/unit-types     [(s/and keyword?
                                     (set (concat common/other-units
                                                  (keys mass/conversion-map)
                                                  (keys volume/conversion-map))))]
   :constants/departments    [(s/and keyword? departments)]
   :constants/roles          [(s/and keyword? roles)]
   :constants/order-statuses [(s/and keyword? (set status))]
   :constants/menu-durations [(s/and keyword? durations)]})
