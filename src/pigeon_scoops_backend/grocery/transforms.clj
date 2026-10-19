(ns pigeon-scoops-backend.grocery.transforms
  (:require [pigeon-scoops-backend.units.common :as common]))

(defn get-unit-keys [amount-unit]
  (->> amount-unit
       (namespace)
       (keyword)
       (get {:mass   [:grocery-unit/unit-mass :grocery-unit/unit-mass-type]
             :volume [:grocery-unit/unit-volume :grocery-unit/unit-volume-type]
             :common [:grocery-unit/unit-common :grocery-unit/unit-common-type]})))

(defn get-optimal-unit [units amount amount-unit]
  (let [[amount-key unit-key] (get-unit-keys amount-unit)
        comparable-amount     (common/to-comparable amount amount-unit)]
    (->> units
         (map #(assoc % :comparable-amount (common/to-comparable (amount-key %) (unit-key %))))
         (filter (comp some? :comparable-amount))
         (sort-by #(Math/abs ^Double (- comparable-amount (:comparable-amount %))))
         (first))))

(defn units-for-amount [units amount amount-unit]
  (let [[amount-key unit-key] (get-unit-keys amount-unit)
        needed-units          (loop [needed-units     []
                                     remaining-amount amount]
                                (let [unit             (get-optimal-unit units remaining-amount amount-unit)
                                      remaining-amount (if unit
                                                         (first (common/reduce-amounts - remaining-amount amount-unit
                                                                                       (amount-key unit) (unit-key unit)))
                                                         remaining-amount)]
                                  (cond (nil? unit) needed-units
                                        (<= remaining-amount 0) (conj needed-units unit)
                                        :else (recur (conj needed-units unit) remaining-amount))))]
    (->> needed-units
         (group-by identity)
         (vals)
         (map (fn [group]
                (assoc (first group) :grocery-unit/quantity (count group)))))))

(defn grocery-for-amount [grocery amount amount-unit]
  (let [[amount-key unit-key]     (get-unit-keys amount-unit)
        units                     (units-for-amount (:grocery/units grocery) amount amount-unit)
        [total-amount total-unit] (when (seq units)
                                    (apply (partial common/reduce-amounts +)
                                           (mapcat #(-> %
                                                        (select-keys [:grocery-unit/quantity amount-key unit-key])
                                                        ((fn [unit]
                                                           [(* (:grocery-unit/quantity unit)
                                                               (amount-key unit))
                                                            (unit-key unit)])))

                                                   units)))
        waste-ratio               (when (seq units)
                                    (->> [amount amount-unit
                                          total-amount total-unit]
                                         (apply (partial common/reduce-amounts /))
                                         (first)
                                         (- 1)))]

    (assoc grocery :grocery/units units
           :grocery/required-amount amount
           :grocery/required-unit amount-unit
           :grocery/purchase-amount total-amount
           :grocery/purchase-unit total-unit
           :grocery/waste-ratio waste-ratio)))
