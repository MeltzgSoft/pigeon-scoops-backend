(ns pigeon-scoops-backend.util-api.handlers
  (:require [pigeon-scoops-backend.auth :refer [roles]]
            [pigeon-scoops-backend.grocery.schema :refer [departments]]
            [pigeon-scoops-backend.menu.schema :refer [durations]]
            [pigeon-scoops-backend.units.common :as common]
            [pigeon-scoops-backend.units.mass :as mass]
            [pigeon-scoops-backend.units.volume :as volume]
            [pigeon-scoops-backend.user-order.schema :refer [status]]
            [ring.util.response :as rr]))

(defn get-constants []
  (fn [_]
    (rr/response {:constants/unit-types     (vec (concat common/other-units
                                                         (keys mass/conversion-map)
                                                         (keys volume/conversion-map)))
                  :constants/departments    (vec departments)
                  :constants/roles          (vec roles)
                  :constants/order-statuses (vec status)
                  :constants/menu-durations (vec durations)})))
