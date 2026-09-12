(ns pigeon-scoops-backend.db
  (:require [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [next.jdbc :as jdbc]
            [next.jdbc.connection :as njc]
            [next.jdbc.result-set :as rs])
  (:import (com.zaxxer.hikari HikariDataSource)
           (java.sql Array)))

(defn retry! [operation {:keys [max-attempts delay-ms sleep-fn]
                         :or   {sleep-fn #(Thread/sleep %)}}]
  (when-not (pos-int? max-attempts)
    (throw (IllegalArgumentException. "max-attempts must be a positive integer")))
  (loop [attempt 1]
    (let [result (try
                   {:value (operation)}
                   (catch Exception exception
                     {:exception exception}))]
      (if-some [exception (:exception result)]
        (if (< attempt max-attempts)
          (do
            (log/warn exception
                      (format "Database connection attempt %d/%d failed; retrying in %d ms"
                              attempt max-attempts delay-ms))
            (sleep-fn delay-ms)
            (recur (inc attempt)))
          (do
            (log/error exception
                       (format "Could not connect to the database after %d attempts"
                               max-attempts))
            (throw exception)))
        (:value result)))))

(defn create-pool! [jdbc-url]
  (let [pool (njc/->pool HikariDataSource {:jdbcUrl jdbc-url})]
    (try
      (with-open [_connection (.getConnection pool)]
        pool)
      (catch Exception exception
        (.close pool)
        (throw exception)))))

(defmethod ig/init-key :db/postgres
  [_ {:keys [jdbc-url connection-max-attempts connection-retry-delay-ms]
      :or   {connection-max-attempts   10
             connection-retry-delay-ms 3000}}]
  (log/info "Configured DB")
  (extend-protocol rs/ReadableColumn
    Array
    (read-column-by-label [v _]
      (vec (.getArray v)))                                  ; Convert the SQL array into a vector
    (read-column-by-index [v _ _]
      (vec (.getArray v))))                                 ; Convert the SQL array into a vector
  (jdbc/with-options
    (retry! #(create-pool! jdbc-url)
            {:max-attempts connection-max-attempts
             :delay-ms    connection-retry-delay-ms})
    jdbc/snake-kebab-opts))

(defmethod ig/halt-key! :db/postgres [_ config]
  (.close ^HikariDataSource (:connectable config)))
