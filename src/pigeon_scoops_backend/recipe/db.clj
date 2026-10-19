(ns pigeon-scoops-backend.recipe.db
  (:require [honey.sql :as hsql]
            [honey.sql.helpers :as h]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [pigeon-scoops-backend.recipe.transforms :as transforms]
            [pigeon-scoops-backend.utils :refer [apply-db-str->keyword
                                                 apply-keyword->db-str
                                                 with-connection!
                                                 combine-amounts]]))

(defn find-all-recipes!
  ([db]
   (find-all-recipes! db false))
  ([db include-deleted?]
   (with-connection!
     db
     (fn [db]
       (map #(apply-db-str->keyword % :recipe/amount-unit)
            (sql/find-by-keys db :recipe
                              (if include-deleted?
                                :all
                                {:deleted false})))))))

(defn find-recipe-by-id! [db recipe-id]
  (with-connection!
    db
    (fn [db]
      (let [[recipe]    (sql/find-by-keys db :recipe {:id recipe-id})
            ingredients (sql/query db (-> (h/select :ingredient/* :recipe/name :grocery/name)
                                          (h/from :ingredient)
                                          (h/left-join :recipe [:= :ingredient/ingredient-recipe-id :recipe/id])
                                          (h/left-join :grocery [:= :ingredient/ingredient-grocery-id :grocery/id])
                                          (h/where [:= :ingredient/recipe-id recipe-id])
                                          (hsql/format)))]
        (when (seq recipe)
          (-> recipe
              (apply-db-str->keyword :recipe/amount-unit)
              (assoc
               :recipe/ingredients (map #(apply-db-str->keyword % :ingredient/amount-unit) ingredients))))))))

(defn insert-recipe! [db recipe]
  (sql/insert! db :recipe (-> recipe
                              (dissoc :recipe/ingredients)
                              (apply-keyword->db-str :recipe/amount-unit)
                              (assoc :recipe/instructions (into-array String (:recipe/instructions recipe))))))

(defn update-recipe! [db recipe]
  (-> (sql/update! db :recipe (-> recipe
                                  (dissoc :recipe/ingredients)
                                  (apply-keyword->db-str :recipe/amount-unit)
                                  (update :recipe/instructions (partial into-array String)))
                   (select-keys recipe [:recipe/id]))
      ::jdbc/update-count
      (pos?)))

(defn delete-recipe! [db recipe-id]
  (-> (sql/update! db :recipe {:deleted true} {:id recipe-id})
      ::jdbc/update-count
      (pos?)))

(defn insert-ingredient! [db ingredient]
  (sql/insert! db, :ingredient (apply-keyword->db-str ingredient :ingredient/amount-unit)))

(defn update-ingredient! [db ingredient]
  (-> (sql/update! db :ingredient (apply-keyword->db-str
                                   (cond-> ingredient
                                     (some? (:ingredient/ingredient-grocery-id ingredient))
                                     (assoc :ingredient/ingredient-recipe-id nil)
                                     (some? (:ingredient/ingredient-recipe-id ingredient))
                                     (assoc :ingredient/ingredient-grocery-id nil))
                                   :ingredient/amount-unit)
                   (select-keys ingredient [:ingredient/id]))
      ::jdbc/update-count
      (pos?)))

(defn delete-ingredient! [db ingredient]
  (-> (sql/delete! db :ingredient ingredient)
      ::jdbc/update-count
      (pos?)))

(defn ingredient-bom! [db {:recipe/keys [id amount amount-unit]}]
  (with-connection!
    db
    (fn [db]
      (loop [curr-recipe-ingredients  [{:ingredient/ingredient-recipe-id id
                                        :ingredient/amount               amount
                                        :ingredient/amount-unit          amount-unit}]
             curr-grocery-ingredients []]
        (if-not (seq curr-recipe-ingredients)
          (combine-amounts curr-grocery-ingredients
                           :ingredient/amount
                           :ingredient/amount-unit
                           :ingredient/ingredient-grocery-id
                           :ingredient/ingredient-recipe-id)
          (let [{:ingredient/keys [ingredient-recipe-id amount amount-unit]} (first curr-recipe-ingredients)
                {:keys [recipe-ingredients grocery-ingredients]}
                (->> (transforms/scale-recipe
                      (find-recipe-by-id! db ingredient-recipe-id)
                      amount
                      amount-unit)
                     :recipe/ingredients
                     (group-by #(if (some? (:ingredient/ingredient-recipe-id %))
                                  :recipe-ingredients
                                  :grocery-ingredients)))]
            (recur (concat (rest curr-recipe-ingredients) recipe-ingredients)
                   (concat curr-grocery-ingredients grocery-ingredients))))))))
