(ns pigeon-scoops-backend.schema)

(def create-response
  [:map
   [:id :uuid]])

(defn sub-schema [schema keep-keys]
  (->> schema
       (rest)
       (filter (comp (set keep-keys) first))
       (concat (take 1 schema))
       (vec)))

