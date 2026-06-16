(ns pigeon-scoops-backend.recipe.handlers
  (:require [pigeon-scoops-backend.grocery.db :refer [find-grocery-by-id!]]
            [pigeon-scoops-backend.grocery.transforms :refer [grocery-for-amount]]
            [pigeon-scoops-backend.recipe.db :as recipe-db]
            [pigeon-scoops-backend.recipe.transforms :as transforms]
            [pigeon-scoops-backend.responses :as responses]
            [pigeon-scoops-backend.utils :refer [with-connection!]]
            [ring.util.response :as rr])
  (:import (java.util UUID)))

(defn list-all-recipes! [db]
  (fn [_]
    (let [recipes (->> (recipe-db/find-all-recipes! db false)
                       (map transforms/anonymize-mystery-recipe))]
      (rr/response (doall recipes)))))

(defn create-recipe! [db]
  (fn [request]
    (let [recipe-id (UUID/randomUUID)
          recipe (-> request :parameters :body)]
      (recipe-db/insert-recipe! db (assoc recipe :id recipe-id))
      (rr/created (str responses/base-url "/recipes/" recipe-id)
                  {:id recipe-id}))))

(defn retrieve-recipe! [db]
  (fn [request]
    (let [recipe-id (-> request :parameters :path :recipe-id)
          {:keys [amount amount-unit]} (-> request
                                           :parameters
                                           :query)
          recipe (transforms/anonymize-mystery-recipe
                  (recipe-db/find-recipe-by-id! db recipe-id))
          scaled-recipe (transforms/scale-recipe recipe amount amount-unit)]
      (cond (not= (nil? amount) (nil? amount-unit))
            (rr/bad-request {:type    "invalid-amount"
                             :message "Both amount and amount-unit must be specified or nil"
                             :data    {:amount amount :amount-unit amount-unit}})
            (not recipe)
            (rr/not-found {:type    "recipe-not-found"
                           :message "Recipe not found"
                           :data    (str "recipe-id " recipe-id)})
            (and (every? some? [amount amount-unit])
                 (nil? scaled-recipe))
            (rr/bad-request {:type    "invalid-amount"
                             :message "Recipe cannot be converted to requested amount unit"
                             :data    (merge (select-keys recipe [:recipe/amount-unit]) {:amount-unit amount-unit})})
            :else
            (rr/response (update (or scaled-recipe recipe)
                                 :recipe/ingredients vec))))))

(defn update-recipe! [db]
  (fn [request]
    (let [recipe-id (-> request :parameters :path :recipe-id)
          recipe (-> request :parameters :body)
          successful? (recipe-db/update-recipe! db (assoc recipe :recipe/id recipe-id))]
      (if successful?
        (rr/status 204)
        (rr/not-found {:type    "recipe-not-found"
                       :message "Recipe not found"
                       :data    (str "recipe-id " recipe-id)})))))

(defn delete-recipe! [db]
  (fn [request]
    (let [recipe-id (-> request :parameters :path :recipe-id)
          successful? (recipe-db/delete-recipe! db recipe-id)]
      (if successful?
        (rr/status 204)
        (rr/not-found {:type    "recipe-not-found"
                       :message "Recipe not found"
                       :data    (str "recipe-id " recipe-id)})))))

(defn create-ingredient! [db]
  (fn [request]
    (let [recipe-id (-> request :parameters :path :recipe-id)
          ingredient (-> request :parameters :body)
          ingredient-id (UUID/randomUUID)]
      (recipe-db/insert-ingredient! db (assoc ingredient :ingredient/recipe-id recipe-id
                                              :ingredient/id ingredient-id))
      (rr/created (str responses/base-url "/recipes/" recipe-id)
                  {:id ingredient-id}))))

(defn update-ingredient! [db]
  (fn [request]
    (let [{:keys [recipe-id ingredient-id]} (-> request
                                                :parameters
                                                :path
                                                (select-keys [:recipe-id :ingredient-id]))
          ingredient (-> request
                         :parameters
                         :body
                         (assoc :ingredient/id ingredient-id
                                :ingredient/recipe-id recipe-id))]
      (if (-> ingredient
              (select-keys [:ingredient/ingredient-recipe-id :ingredient/ingredient-grocery-id])
              (vals)
              ((partial remove nil?))
              (count)
              (not= 1))
        (rr/bad-request {:type    "invalid-type"
                         :message "Ingredient must be either a grocery or recipe ingredient, exclusive"
                         :data    ingredient})
        (let [successful? (recipe-db/update-ingredient! db ingredient)]
          (if successful?
            (rr/status 204)
            (rr/bad-request (select-keys ingredient [:id]))))))))

(defn delete-ingredient! [db]
  (fn [request]
    (let [{:keys [recipe-id ingredient-id]} (-> request
                                                :parameters
                                                :path
                                                (select-keys [:recipe-id :ingredient-id]))
          successful? (recipe-db/delete-ingredient! db {:ingredient/id ingredient-id
                                                        :ingredient/recipe-id recipe-id})]
      (if successful?
        (rr/status 204)
        (rr/bad-request {:ingredient/id ingredient-id})))))

(defn retrieve-recipe-bom! [db]
  (fn [request]
    (with-connection!
      db
      (fn [db]
        (let [recipe-id (-> request :parameters :path :recipe-id)
              {:keys [amount amount-unit]} (-> request
                                               :parameters
                                               :query)
              ingredient-bom (recipe-db/ingredient-bom! db {:recipe/id          recipe-id
                                                            :recipe/amount      amount
                                                            :recipe/amount-unit amount-unit})
              grocery-bom (map #(update (grocery-for-amount
                                         (find-grocery-by-id! db (:ingredient/ingredient-grocery-id %))
                                         (:ingredient/amount %)
                                         (:ingredient/amount-unit %))
                                        :grocery/units
                                        vec)
                               ingredient-bom)]
          (rr/response (vec grocery-bom)))))))
