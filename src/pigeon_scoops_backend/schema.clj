(ns pigeon-scoops-backend.schema)

(def create-response
  [:map
   [:id :uuid]])

(defn update-options [attr-schema opts]
  (if (= 2 (count attr-schema))
    [(first attr-schema) opts (last attr-schema)]
    (update attr-schema 1 #(merge % opts))))

(defn sub-schema
  "Extract a schema from a subset of attributes. keep-keys can contain keywords and [keyword option-overrides]"
  [schema keep-keys]
  (let [schema-keys      (map #(if (keyword? %) % (first %)) keep-keys)
        option-overrides (->> keep-keys
                              (filter #(not (keyword? %)))
                              (into {}))]
    (->> schema
         (rest)
         (filter (comp (set schema-keys) first))
         (map #(if ((first %) option-overrides)
                 (update-options % ((first %) option-overrides))
                 %))
         (concat (take 1 schema))
         (vec))))

