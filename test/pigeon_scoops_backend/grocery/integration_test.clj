(ns pigeon-scoops-backend.grocery.integration-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [pigeon-scoops-backend.test-system :as ts]))

(use-fixtures :once ts/system-fixture (ts/make-account-fixture) (ts/make-roles-fixture :manage-groceries))

(def grocery
  {:grocery/name "orange" :grocery/department :department/produce})

(def updated-grocery
  (assoc grocery :grocery/name "lime"))

(def grocery-unit
  {:grocery-unit/source         "store"
   :grocery-unit/unit-cost      3.3
   :grocery-unit/unit-mass      420
   :grocery-unit/unit-mass-type :mass/kg})

(def updated-grocery-unit
  (assoc grocery-unit
         :grocery-unit/unit-common-type :common/unit
         :grocery-unit/unit-common 1000))

(deftest groceries-list-test
  (testing "List groceries"
    (let [{:keys [status body]} (ts/test-endpoint :get "/v1/groceries" {:use-auth? true})]
      (is (= 200 status))
      (is (vector? body)))))

(deftest groceries-crud-test
  (let [grocery-id      (atom nil)
        grocery-unit-id (atom nil)]
    (testing "create grocery"
      (let [{:keys [status body]} (ts/test-endpoint :post "/v1/groceries" {:use-auth? true :body grocery})]
        (reset! grocery-id (:id body))
        (is (= status 201))))
    (testing "update grocery"
      (let [{:keys [status body]} (ts/test-endpoint :put (str "/v1/groceries/" @grocery-id) {:use-auth? true :body updated-grocery})]
        (is (= status 204) (str "oops " status " : " body))))
    (testing "create grocery-unit"
      (let [{:keys [status body]} (ts/test-endpoint :post (str "/v1/groceries/" @grocery-id "/units") {:use-auth? true :body grocery-unit})]
        (reset! grocery-unit-id (:id body))
        (is (= status 201))))
    (testing "update grocery-unit"
      (let [{:keys [status]} (ts/test-endpoint :put (str "/v1/groceries/" @grocery-id "/units/" @grocery-unit-id) {:use-auth? true :body updated-grocery-unit})]
        (is (= status 204))))
    (testing "delete grocery-unit"
      (let [{:keys [status]} (ts/test-endpoint :delete (str "/v1/groceries/" @grocery-id "/units/" @grocery-unit-id) {:use-auth? true})]
        (is (= status 204))))
    (testing "delete grocery"
      (let [{:keys [status]} (ts/test-endpoint :delete (str "/v1/groceries/" @grocery-id) {:use-auth? true})]
        (is (= status 204))))))
