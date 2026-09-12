(ns pigeon-scoops-backend.server
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [pigeon-scoops-backend.utils :refer [load-config! init-system!]]
            [pigeon-scoops-backend.router :as router]
            [ring.adapter.jetty :as jetty]
            [pigeon-scoops-backend.db]
            [pigeon-scoops-backend.db-tasks])
  (:import (org.eclipse.jetty.server Server)))

(defmethod ig/init-key :server/jetty [_ {:keys [handler port]}]
  (log/info "Server running on port" port)
  (jetty/run-jetty handler {:port port :join? false}))

(defmethod ig/init-key :server/routes [_ config]
  (log/info "Starting app")
  (router/routes config))

(defmethod ig/halt-key! :server/jetty [_ ^Server jetty]
  (log/info "Stopping server")
  (.stop jetty))

(defn -main
  [config-file & _]
  (-> config-file
      (load-config!)
      (init-system!)))
