(ns pigeon-scoops-backend.utils
  (:require [environ.core :refer [env]]
            [integrant.core :as ig]
            [next.jdbc :as jdbc]
            [pigeon-scoops-backend.units.common :as common])
  (:import (java.time Duration)))

(defn read-env! [form]
  (if (keyword? form)
    (env form)
    (let [[var-type var-name var-default] form
          value (env var-name)
          parser (case var-type
                   :int parse-long
                   :double parse-double
                   :boolean parse-boolean
                   :uuid parse-uuid
                   identity)]
      (or (parser value)
          var-default))))

(defn load-config! [config-file]
  (->> config-file
       (slurp)
       (ig/read-string
        {:readers {'env read-env!}})))

(defn init-system! [config]
  (-> config
      (ig/expand)
      (ig/init)))

(defn keyword->db-str [k]
  (subs (str k) 1))

(defn apply-db-str->keyword [entity & keys]
  (reduce (fn [acc k] (if (contains? entity k)
                        (update acc k keyword)
                        acc))
          entity keys))

(defn apply-keyword->db-str [entity & keys]
  (reduce (fn [acc k]
            (if (contains? entity k)
              (update acc k #(subs (str %) 1))
              acc))
          entity keys))

(defn with-connection! [db f]
  (try
    (with-open [conn (jdbc/get-connection db)]
      (let [db (jdbc/with-options conn (:options db))]
        (f db)))
    (catch IllegalArgumentException _
      (f db))))

(defn end-time [now duration duration-type]
  (case duration-type
    :duration/day (.plus now (Duration/ofDays duration))
    :duration/week (.plusWeeks now duration)
    :duration/month (.plusMonths now duration)))

(defn remove-nil-keys [value]
  (cond (map? value)
        (update-vals
         (->> value
              (remove (comp nil? val))
              (into {}))
         remove-nil-keys)
        (coll? value)
        (map remove-nil-keys value)
        :else
        value))

(defn doall-deep [value]
  (cond (map? value)
        (update-vals value doall-deep)
        (or (coll? value) (seq? value))
        (mapv doall-deep value)
        :else
        value))

(defn production-manager? [request]
  (-> request
      (get-in [:claims "https://api.pigeon-scoops.com/perms"])
      (set)
      (#(% "manage:production"))))

(defn combine-amounts [amounts amount-key amount-unit-key & type-discriminant-keys]
  (as-> amounts ?
    (group-by (comp #(update % amount-unit-key namespace)
                    #(select-keys % (concat [amount-unit-key]
                                            type-discriminant-keys)))
              ?)
    (update-vals ? (partial reduce
                            (fn [{sink-amount-unit amount-unit-key :as acc}
                                 entity]
                              (let [scaled-amount (common/convert (get entity amount-key)
                                                                  (get entity amount-unit-key)
                                                                  sink-amount-unit)]
                                (update acc amount-key + scaled-amount)))))
    (vals ?)))
