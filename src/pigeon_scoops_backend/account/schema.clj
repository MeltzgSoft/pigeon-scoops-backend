(ns pigeon-scoops-backend.account.schema
  (:require
   [pigeon-scoops-backend.auth :refer [roles]]))

(def roles-enum
  (vec (concat [:enum] roles)))

(def account
  [:map
   [:account/id :string]
   [:account/name :string]
   [:account/roles roles-enum]])

(def list-response
  [:vector account])

(def user-update-req
  [:map
   [:roles [:vector roles-enum]]])
