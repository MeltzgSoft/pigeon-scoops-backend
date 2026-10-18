(ns pigeon-scoops-backend.util-api.routes
  (:require [pigeon-scoops-backend.util-api.handlers :as util-api]
            [pigeon-scoops-backend.util-api.schema :as responses]))

(defn routes []
  ["" {:openapi    {:tags ["utilities"]}}
   ["/constants" {:get {:handler   (util-api/get-constants)
                        :responses {200 {:body responses/constants}}
                        :summary   "retrieve application constants"}}]])
