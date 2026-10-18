(ns pigeon-scoops-backend.account.routes
  (:require [clojure.spec.alpha :as s]
            [pigeon-scoops-backend.account.schema :as responses]
            [pigeon-scoops-backend.account.handlers :as account]
            [pigeon-scoops-backend.auth :as auth0]
            [pigeon-scoops-backend.middleware :as mw]))

(def wrap-edit-roles
  (mw/wrap-with-permission :edit/role))

(defn routes [{:keys [auth] db :jdbc-url}]
  ["/account" {:openapi    {:tags ["account"]}}
   ["" {:get {:handler (account/get-accounts! auth db)
              :middleware [[wrap-edit-roles]]
              :responses {200 {:body [responses/account]}}
              :summary "Get account information"}
        :post   {:handler   (account/create-account! db)
                 :responses {201 {:body nil?}}
                 :summary   "Create account"}
        :delete {:handler   (account/delete-account! auth db)
                 :responses {204 {:body nil?}}
                 :summary   "Delete account"}}]
   ["/:user-id" {:parameters {:path {:user-id string?}}
                 :put        {:handler    (account/update-roles! auth)
                              :middleware [[wrap-edit-roles]]
                              :parameters {:body {:roles [(s/and keyword? auth0/roles)]}}
                              :responses  {204 {:body nil?}}
                              :summary    "Update user roles"}}]])
