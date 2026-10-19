(ns pigeon-scoops-backend.db-tasks
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.tools.cli :as cli]
            [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [pigeon-scoops-backend.menu.db :as menu-db]
            [pigeon-scoops-backend.user-order.db :as order-db]
            [pigeon-scoops-backend.utils :refer [end-time with-connection! load-config! init-system!]]
            [tea-time.core :as tt]
            [pigeon-scoops-backend.db])
  (:import (java.time ZonedDateTime)
           (org.flywaydb.core Flyway)))

(def options
  [["-c" "--config-file CONFIG_FILE" "Config File"
    :validate [#(.exists (io/file %))]]
   ["-t" "--task TASK" "DB Task"
    :parse-fn keyword]])

(defn expired-menus [menus now]
  (filter #(when (:menu/end-time %)
             (> (.toEpochSecond now) (.getEpochSecond (.toInstant (:menu/end-time %)))))
          menus))

(defn recipes-to-accept [active-items expired-menu-ids]
  (->> active-items
       (filter #(expired-menu-ids (:menu-item/menu-id %)))
       (mapv :menu-item/recipe-id)))

(defn accept-orders! [jdbc-url]
  (log/info "Accepting orders on expired menus at" (.toString (ZonedDateTime/now)))
  (with-connection!
    jdbc-url
    (fn [db]
      (let [now         (ZonedDateTime/now)
            expired     (expired-menus (menu-db/find-all-menus! db) now)
            expired-ids (set (map :menu/id expired))
            recipes     (recipes-to-accept (menu-db/find-active-menu-items! db) expired-ids)]
        (when (seq recipes)
          (apply (partial order-db/bulk-status-update! db {:status/submitted :status/in-progress
                                                           :status/draft     :status/canceled})
                 recipes))
        (dorun (->> expired
                    (remove :menu/repeats)
                    (map #(menu-db/update-menu! db (assoc % :menu/active false)))))
        (dorun (->> expired
                    (filter :menu/repeats)
                    (map #(menu-db/update-menu! db (assoc % :menu/end-time (end-time now
                                                                                     (:menu/duration %)
                                                                                     (:menu/duration-type %)))))))))))

(defmethod ig/init-key :db-tasks/migration [_ {:keys [jdbc-url]}]
  (fn []
    (log/info "Migrating database")
    (-> (Flyway/configure)
        (.dataSource (:connectable jdbc-url))
        (.locations (into-array String ["classpath:db/migrations"]))
        (.load)
        (.migrate))))

(defmethod ig/init-key :db-tasks/accept-orders [_ {:keys [jdbc-url]}]
  (fn []
    (accept-orders! jdbc-url)))

(defmethod ig/init-key :db-tasks/scheduler [_ {:keys [jdbc-url accept-orders-interval-seconds]}]
  (log/info "Starting task scheduler")
  (tt/start!)
  (tt/every! accept-orders-interval-seconds (bound-fn [] (accept-orders! jdbc-url))))

(defmethod ig/halt-key! :db-tasks/scheduler [_ task]
  (log/info "Stopping scheduled task")
  (tt/cancel! task)
  (log/info "Stopping task scheduler")
  (tt/stop!))

(defn -main
  [& args]
  (let [opts (cli/parse-opts args options)]
    (if (:errors opts)
      (log/error (:errors opts))
      (let [system (-> opts
                       :options
                       :config-file
                       (load-config!)
                       (init-system!))
            task   (->> opts
                        :options
                        :task
                        (get system))]
        (task)
        (ig/halt! system)))))
