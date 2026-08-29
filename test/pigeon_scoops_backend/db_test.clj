(ns pigeon-scoops-backend.db-test
  (:require [clojure.test :refer [deftest is testing]]
            [pigeon-scoops-backend.db :as db]))

(deftest retry!-test
  (testing "returns immediately after a successful connection"
    (let [attempts (atom 0)
          delays (atom [])]
      (is (= :connected
             (db/retry! #(do (swap! attempts inc) :connected)
                        {:max-attempts 3
                         :delay-ms    50
                         :sleep-fn   #(swap! delays conj %)})))
      (is (= 1 @attempts))
      (is (empty? @delays))))
  (testing "retries failed connections up to the configured maximum"
    (let [attempts (atom 0)
          delays (atom [])]
      (is (= :connected
             (db/retry! #(if (< (swap! attempts inc) 3)
                           (throw (ex-info "database unavailable" {}))
                           :connected)
                        {:max-attempts 3
                         :delay-ms    50
                         :sleep-fn   #(swap! delays conj %)})))
      (is (= 3 @attempts))
      (is (= [50 50] @delays))))
  (testing "rethrows the connection error when all attempts fail"
    (let [attempts (atom 0)
          failure (ex-info "database unavailable" {})]
      (is (identical? failure
                      (try
                        (db/retry! #(do (swap! attempts inc) (throw failure))
                                   {:max-attempts 3
                                    :delay-ms    0
                                    :sleep-fn   (constantly nil)})
                        (catch Exception exception
                          exception))))
      (is (= 3 @attempts))))
  (testing "rejects a non-positive maximum attempt count"
    (is (thrown-with-msg? IllegalArgumentException
                          #"max-attempts must be a positive integer"
                          (db/retry! (constantly :connected)
                                     {:max-attempts 0
                                      :delay-ms    50
                                      :sleep-fn   (constantly nil)})))))
