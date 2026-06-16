(ns pigeon-scoops-backend.grocery.handlers
  (:require [pigeon-scoops-backend.grocery.db :as grocery-db]
            [pigeon-scoops-backend.responses :as responses]
            [ring.util.response :as rr])
  (:import (java.util UUID)))

(defn list-all-groceries! [db]
  (fn [_]
    (rr/response (vec (grocery-db/find-all-groceries! db)))))

(defn create-grocery! [db]
  (fn [request]
    (let [grocery-id (UUID/randomUUID)
          grocery (-> request :parameters :body)]
      (grocery-db/insert-grocery! db (assoc grocery :grocery/id grocery-id))
      (rr/created (str responses/base-url "/groceries/" grocery-id)
                  {:id grocery-id}))))

(defn retrieve-grocery! [db]
  (fn [request]
    (let [grocery-id (-> request :parameters :path :grocery-id)
          grocery (grocery-db/find-grocery-by-id! db grocery-id)]
      (if grocery
        (rr/response (update grocery :grocery/units vec))
        (rr/not-found {:type    "grocery-not-found"
                       :message "grocery not found"
                       :data    (str "grocery-id " grocery-id)})))))

(defn update-grocery! [db]
  (fn [request]
    (let [grocery-id (-> request :parameters :path :grocery-id)
          grocery (-> request :parameters :body)
          successful? (grocery-db/update-grocery! db (assoc grocery :grocery/id grocery-id))]
      (if successful?
        (rr/status 204)
        (rr/not-found {:type    "grocery-not-found"
                       :message "grocery not found"
                       :data    (str "grocery-id " grocery-id)})))))

(defn delete-grocery! [db]
  (fn [request]
    (let [grocery-id (-> request :parameters :path :grocery-id)
          successful? (grocery-db/delete-grocery! db grocery-id)]
      (if successful?
        (rr/status 204)
        (rr/not-found {:type    "grocery-not-found"
                       :message "grocery not found"
                       :data    (str "grocery-id " grocery-id)})))))

(defn create-grocery-unit! [db]
  (fn [request]
    (let [grocery-id (-> request :parameters :path :grocery-id)
          grocery-unit (-> request :parameters :body)
          grocery-unit-id (UUID/randomUUID)]
      (grocery-db/insert-grocery-unit! db (assoc grocery-unit :grocery-unit/grocery-id grocery-id
                                                 :grocery-unit/id grocery-unit-id))
      (rr/created (str responses/base-url "/groceries/" grocery-id)
                  {:id grocery-unit-id}))))

(defn update-grocery-unit! [db]
  (fn [request]
    (let [{:keys [grocery-id unit-id]} (-> request
                                           :parameters
                                           :path
                                           (select-keys [:grocery-id :unit-id]))
          grocery-unit (-> request
                           :parameters
                           :body
                           (assoc :grocery-unit/id unit-id
                                  :grocery-unit/grocery-id grocery-id))
          successful? (grocery-db/update-grocery-unit! db grocery-unit)]
      (if successful?
        (rr/status 204)
        (rr/bad-request (select-keys grocery-unit [:id]))))))

(defn delete-grocery-unit! [db]
  (fn [request]
    (let [{:keys [grocery-id unit-id]} (-> request
                                           :parameters
                                           :path
                                           (select-keys [:grocery-id :unit-id]))
          successful? (grocery-db/delete-grocery-unit! db {:grocery-unit/id unit-id
                                                           :grocery-unit/grocery-id grocery-id})]
      (if successful?
        (rr/status 204)
        (rr/bad-request (-> request :parameters :body))))))
