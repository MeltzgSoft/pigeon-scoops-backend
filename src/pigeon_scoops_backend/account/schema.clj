(ns pigeon-scoops-backend.account.schema
  (:require
   [clojure.spec.alpha :as s]
   [pigeon-scoops-backend.auth :refer [roles]]))

(def account
  {:account/id string?
   :account/name string?
   :account/roles [(s/and keyword?
                          roles)]})
