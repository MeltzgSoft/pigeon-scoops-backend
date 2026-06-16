(ns pigeon-scoops-backend.utils-test
  (:require [clojure.test :refer [are deftest is testing]]
            [pigeon-scoops-backend.utils :as utils])
  (:import (java.time ZonedDateTime)))

(deftest keyword->db-str-test
  (testing "converts a keyword to its string representation without the leading colon"
    (are [k expected] (= (utils/keyword->db-str k) expected)
      :foo/bar "foo/bar"
      :mass/kg "mass/kg"
      :status/draft "status/draft")))

(deftest apply-db-str->keyword-test
  (testing "converts specified string keys to keywords"
    (is (= {:entity/type :mass/kg :entity/name "flour"}
           (utils/apply-db-str->keyword {:entity/type "mass/kg" :entity/name "flour"}
                                        :entity/type))))
  (testing "leaves keys absent from the entity unchanged"
    (is (= {:entity/name "flour"}
           (utils/apply-db-str->keyword {:entity/name "flour"} :entity/type))))
  (testing "applies conversion to multiple keys"
    (is (= {:entity/type :mass/kg :entity/unit :mass/g}
           (utils/apply-db-str->keyword {:entity/type "mass/kg" :entity/unit "mass/g"}
                                        :entity/type :entity/unit)))))

(deftest apply-keyword->db-str-test
  (testing "converts specified keyword keys to strings"
    (is (= {:entity/type "mass/kg" :entity/name "flour"}
           (utils/apply-keyword->db-str {:entity/type :mass/kg :entity/name "flour"}
                                        :entity/type))))
  (testing "leaves keys absent from the entity unchanged"
    (is (= {:entity/name "flour"}
           (utils/apply-keyword->db-str {:entity/name "flour"} :entity/type))))
  (testing "applies conversion to multiple keys"
    (is (= {:entity/type "mass/kg" :entity/unit "mass/g"}
           (utils/apply-keyword->db-str {:entity/type :mass/kg :entity/unit :mass/g}
                                        :entity/type :entity/unit)))))

(deftest end-time-test
  (let [now (ZonedDateTime/now)]
    (testing ":duration/day adds days"
      (is (= (.plusDays now 3) (utils/end-time now 3 :duration/day))))
    (testing ":duration/week adds weeks"
      (is (= (.plusWeeks now 2) (utils/end-time now 2 :duration/week))))
    (testing ":duration/month adds months"
      (is (= (.plusMonths now 1) (utils/end-time now 1 :duration/month))))))

(deftest remove-nil-keys-test
  (testing "removes top-level nil values"
    (is (= {:a 1} (utils/remove-nil-keys {:a 1 :b nil}))))
  (testing "removes nil values recursively in nested maps"
    (is (= {:a {:b 2}} (utils/remove-nil-keys {:a {:b 2 :c nil}}))))
  (testing "removes nil values inside collections"
    (is (= {:a [{:b 1}]} (utils/remove-nil-keys {:a [{:b 1 :c nil}]}))))
  (testing "empty map returns empty map"
    (is (= {} (utils/remove-nil-keys {}))))
  (testing "non-map scalars are returned as-is"
    (is (= 42 (utils/remove-nil-keys 42)))
    (is (= "hello" (utils/remove-nil-keys "hello")))))

(deftest doall-deep-test
  (testing "realizes lazy sequences inside a map"
    (let [result (utils/doall-deep {:a (map inc [1 2 3])})]
      (is (vector? (:a result)))
      (is (= [2 3 4] (:a result)))))
  (testing "realizes nested lazy sequences"
    (let [result (utils/doall-deep [(map inc [1 2])])]
      (is (vector? result))
      (is (= [[2 3]] result))))
  (testing "scalars pass through unchanged"
    (is (= 5 (utils/doall-deep 5)))
    (is (= "hi" (utils/doall-deep "hi")))))

(deftest production-manager?-test
  (testing "returns truthy when the request has the manage:production permission"
    (let [request {:claims {"https://api.pigeon-scoops.com/perms" ["manage:production" "view:grocery"]}}]
      (is (utils/production-manager? request))))
  (testing "returns falsy when the permission is absent"
    (let [request {:claims {"https://api.pigeon-scoops.com/perms" ["view:grocery"]}}]
      (is (not (utils/production-manager? request)))))
  (testing "returns falsy when perms key is missing"
    (is (not (utils/production-manager? {:claims {}})))))

(deftest combine-amounts-test
  (testing "empty input returns empty"
    (is (nil? (utils/combine-amounts [] :entity/amount :entity/amount-unit :entity/id))))
  (testing "single item passes through unchanged"
    (is (= [{:entity/id "a" :entity/amount 5 :entity/amount-unit :mass/kg}]
           (utils/combine-amounts [{:entity/id "a" :entity/amount 5 :entity/amount-unit :mass/kg}]
                                  :entity/amount :entity/amount-unit :entity/id))))
  (testing "two items with same unit and discriminant are summed"
    (is (= [{:entity/id "a" :entity/amount 7 :entity/amount-unit :mass/kg}]
           (utils/combine-amounts [{:entity/id "a" :entity/amount 3 :entity/amount-unit :mass/kg}
                                   {:entity/id "a" :entity/amount 4 :entity/amount-unit :mass/kg}]
                                  :entity/amount :entity/amount-unit :entity/id))))
  (testing "nil discriminant key values group together"
    (is (= 1 (count (utils/combine-amounts [{:entity/amount 1 :entity/amount-unit :mass/kg}
                                            {:entity/amount 2 :entity/amount-unit :mass/kg}]
                                           :entity/amount :entity/amount-unit :entity/id)))))
  (testing "different discriminants produce separate groups"
    (is (= 2 (count (utils/combine-amounts [{:entity/id "a" :entity/amount 1 :entity/amount-unit :mass/kg}
                                            {:entity/id "b" :entity/amount 2 :entity/amount-unit :mass/kg}]
                                           :entity/amount :entity/amount-unit :entity/id)))))
  (testing "mixed units within same discriminant group are converted to the sink unit"
    (let [[result] (utils/combine-amounts [{:entity/id "a" :entity/amount 1 :entity/amount-unit :mass/kg}
                                           {:entity/id "a" :entity/amount 1000 :entity/amount-unit :mass/g}]
                                          :entity/amount :entity/amount-unit :entity/id)]
      (is (= :mass/kg (:entity/amount-unit result)))
      (is (< (abs (- 2.0 (:entity/amount result))) 0.0001))))
  (testing "A list of amounts can be combined by discriminant keys and unit type"
    (is (= (utils/combine-amounts [{:entity/entity-discriminant-key-1 "good stuff"
                                    :entity/amount               1
                                    :entity/amount-unit          :mass/kg}
                                   {:entity/entity-discriminant-key-1 "good stuff"
                                    :entity/amount               2.2
                                    :entity/amount-unit          :mass/lb}
                                   {:entity/entity-discriminant-key-1 "good stuff"
                                    :entity/amount               2
                                    :entity/amount-unit          :volume/gal}
                                   {:entity/entity-discriminant-key-1 "other stuff"
                                    :entity/amount               3
                                    :entity/amount-unit          :mass/kg}
                                   {:entity/entity-discriminant-key-1 "other stuff"
                                    :entity/amount               3
                                    :entity/amount-unit          :mass/kg}
                                   {:entity/entity-discriminant-key-2 "other stuff"
                                    :entity/amount               3
                                    :entity/amount-unit          :mass/kg}
                                   {:entity/entity-discriminant-key-2 "other stuff"
                                    :entity/amount               3
                                    :entity/amount-unit          :mass/kg}
                                   {:entity/entity-discriminant-key-1 "good stuff"
                                    :entity/entity-discriminant-key-2 "other stuff"
                                    :entity/amount               2
                                    :entity/amount-unit          :volume/gal}
                                   {:entity/entity-discriminant-key-1 "good stuff"
                                    :entity/entity-discriminant-key-2 "other stuff"
                                    :entity/amount               2
                                    :entity/amount-unit          :volume/qt}]
                                  :entity/amount
                                  :entity/amount-unit
                                  :entity/entity-discriminant-key-1
                                  :entity/entity-discriminant-key-2)
           [{:entity/entity-discriminant-key-1 "good stuff"
             :entity/amount               1.9979024
             :entity/amount-unit          :mass/kg}
            {:entity/entity-discriminant-key-1 "good stuff"
             :entity/amount               2
             :entity/amount-unit          :volume/gal}
            {:entity/entity-discriminant-key-1 "other stuff"
             :entity/amount               6
             :entity/amount-unit          :mass/kg}
            {:entity/entity-discriminant-key-2 "other stuff"
             :entity/amount               6
             :entity/amount-unit          :mass/kg}
            {:entity/entity-discriminant-key-1 "good stuff"
             :entity/entity-discriminant-key-2 "other stuff"
             :entity/amount               2.5
             :entity/amount-unit          :volume/gal}]))))
