(ns pigeon-scoops-backend.account.routes
  (:require [pigeon-scoops-backend.account.schema :as schema]
            [pigeon-scoops-backend.account.handlers :as account]
            [pigeon-scoops-backend.middleware :as mw]))

(def wrap-edit-roles
  (mw/wrap-with-permission :edit/role))

(defn routes [{:keys [auth] db :jdbc-url}]
  ["/account" {:openapi {:tags ["account"]}}
   ["" {:get    {:handler    (account/get-accounts! auth db)
                 :middleware [[wrap-edit-roles]]
                 :responses  {200 {:body schema/list-response}}
                 :summary    "Get account information"}
        :post   {:handler   (account/create-account! db)
                 :responses {201 {:body :nil}}
                 :summary   "Create account"}
        :delete {:handler   (account/delete-account! auth db)
                 :responses {204 {:body :nil}}
                 :summary   "Delete account"}}]
   ["/:user-id" {:parameters {:path [:map [:user-id :string]]}
                 :put        {:handler    (account/update-roles! auth)
                              :middleware [[wrap-edit-roles]]
                              :parameters {:body schema/user-update-req}
                              :responses  {204 {:body :nil}}
                              :summary    "Update user roles"}}]])
