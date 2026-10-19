(ns pigeon-scoops-backend.account.schema
  (:require
   [pigeon-scoops-backend.auth :refer [roles]]))

(def account
  [:map
   [:account/id :string]
   [:account/name :string]
   [:account/roles (vec (concat [:enum] roles))]])

