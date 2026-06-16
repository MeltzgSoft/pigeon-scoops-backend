(ns pigeon-scoops-backend.router
  (:require [muuntaja.core :as m]
            [pigeon-scoops-backend.account.routes :as account]
            [pigeon-scoops-backend.grocery.routes :as grocery]
            [pigeon-scoops-backend.menu.routes :as menu]
            [pigeon-scoops-backend.middleware :as mw]
            [pigeon-scoops-backend.recipe.routes :as recipe]
            [pigeon-scoops-backend.user-order.routes :as user-order]
            [pigeon-scoops-backend.util-api.routes :as util-api]
            [reitit.coercion.spec :as coercion-spec]
            [reitit.dev.pretty :as pretty]
            [reitit.openapi :as openapi]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as coercion]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.spec :as rs]
            [ring.logger :refer [wrap-with-logger]]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.params :refer [wrap-params]]))

(defn- router-config [jwt-config]
  {:validate  rs/validate
   :exception pretty/exception
   :data      {:coercion   coercion-spec/coercion
               :muuntaja   m/instance
               :middleware [[wrap-cors
                             :access-control-allow-origin [#"http://localhost:3000"
                                                           #"https://pigeon-scoops.com"]
                             :access-control-allow-methods [:get :patch :post :put :delete]]
                            swagger/swagger-feature
                            muuntaja/format-middleware
                            (exception/create-exception-middleware
                             (assoc exception/default-handlers
                                    ::exception/wrap exception/wrap-log-to-console))
                            wrap-params
                            coercion/coerce-request-middleware
                            coercion/coerce-response-middleware
                            (mw/wrap-auth0 jwt-config)
                            mw/wrap-remove-nil-keys]}})

(def openapi-docs
  ["/openapi.json"
   {:get {:no-doc  true
          :openapi {:info       {:title       "Pigeon Scoops Backend Reference"
                                 :description "The Pigeon Scoops Backend API is organized around REST. Returns JSON, Transit (msgpack, json), or EDN  encoded responses."
                                 :version     "1.0.0"}
                    :components {:securitySchemes {"auth" {:type         :http
                                                           :scheme       :bearer
                                                           :bearerFormat "JWT"}}}}
          :handler (openapi/create-openapi-handler)}}])

(defn routes [{:keys [auth] :as env}]
  (wrap-with-logger
   (ring/ring-handler
    (ring/router
     [openapi-docs
      ["/v1"
       {:openapi {:security [{"auth" []}]}}
       (grocery/routes env)
       (recipe/routes env)
       (menu/routes env)
       (user-order/routes env)
       (util-api/routes)
       (account/routes env)]]
     (router-config (:jwt-config auth)))
    (ring/routes
     (swagger-ui/create-swagger-ui-handler
      {:path   "/"
       :config {:validatorUrl     nil
                :urls             [{:name "openapi", :url "openapi.json"}]
                :urls.primaryName "openapi"
                :operationsSorter "alpha"}})
     (ring/create-default-handler)))))
