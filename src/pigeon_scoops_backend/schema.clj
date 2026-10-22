(ns pigeon-scoops-backend.schema
  (:require [clojure.set :as set]))

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
  (let [keys-to-keep     (map #(if (keyword? %) % (first %)) keep-keys)
        option-overrides (->> keep-keys
                              (filter #(not (keyword? %)))
                              (into {}))]
    (when-not (set/subset? (set keys-to-keep) (set (map first (rest schema))))
      (throw (ex-info "Original schema is missing keys from sub-schema request"
                      {:schema    schema
                       :keep-keys keep-keys})))
    (->> schema
         (rest)
         (filter (comp (set keys-to-keep) first))
         (map #(if ((first %) option-overrides)
                 (update-options % ((first %) option-overrides))
                 %))
         (concat (take 1 schema))
         (vec))))

