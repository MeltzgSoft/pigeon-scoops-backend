(ns pigeon-scoops-backend.user-order.responses-test
  (:require [clojure.test :refer [deftest is testing]]
            [pigeon-scoops-backend.user-order.schema :refer [terminal?]]))

(deftest terminal?-test
  (testing "terminal statuses"
    (is (terminal? :status/complete))
    (is (terminal? :status/in-progress)))
  (testing "non-terminal statuses"
    (is (not (terminal? :status/draft)))
    (is (not (terminal? :status/submitted)))
    (is (not (terminal? :status/canceled))))
  (testing "nil and unknown values are not terminal"
    (is (not (terminal? nil)))
    (is (not (terminal? :status/unknown)))))
