(ns pigeon-scoops-backend.accounts-test
  (:require [clojure.test :refer [deftest testing use-fixtures]]
            [pigeon-scoops-backend.test-system :as ts])
  (:import (java.net URLEncoder)))

(use-fixtures :once ts/system-fixture (ts/make-account-fixture false) (ts/make-roles-fixture :manage-roles))

(deftest account-tests
  (testing "Create user account"
    (ts/test-endpoint :post "/v1/account" {:use-auth? true}))
  (testing "Create duplicate user no-op"
    (ts/test-endpoint :post "/v1/account" {:use-auth? true :expected-status 204}))
  (testing "Update user role"
    (ts/test-endpoint :put (str "/v1/account/" (URLEncoder/encode ^String (:uid (first @ts/test-users)) "UTF-8"))
                      {:use-auth? true
                       :body      {:roles [:manage-recipes :manage-groceries]}}))
  (testing "Delete user account"
    (ts/test-endpoint :delete "/v1/account" {:use-auth? true})))
