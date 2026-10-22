(ns pigeon-scoops-backend.recipe.routes
  (:require [pigeon-scoops-backend.schema :refer [create-response sub-schema]]
            [pigeon-scoops-backend.grocery.schema :refer [grocery]]
            [pigeon-scoops-backend.middleware :as mw]
            [pigeon-scoops-backend.recipe.handlers :as recipe]
            [pigeon-scoops-backend.recipe.schema :as schema]))

(def create-recipe
  (sub-schema schema/recipe [:recipe/name
                             :recipe/source
                             :recipe/is-mystery
                             :recipe/description
                             :recipe/mystery-description
                             :recipe/instructions
                             :recipe/amount
                             :recipe/amount-unit]))

(def get-recipe-query
  (->> [[:recipe/amount {:optional true}]
        [:recipe/amount-unit {:optional true}]]
       (sub-schema schema/recipe)
       (mapv #(if (keyword? %) % (update % 0 (comp keyword name))))))

(def create-ingredient
  (sub-schema schema/ingredient [:ingredient/amount
                                 :ingredient/amount-unit
                                 :ingredient/ingredient-grocery-id
                                 :ingredient/ingredient-recipe-id]))

(defn routes [{db :jdbc-url}]
  ["/recipes" {:openapi {:tags ["recipes"]}}
   ["" {:get  {:handler   (recipe/list-all-recipes! db)
               :responses {200 {:body [:vector schema/recipe]}}
               :summary   "list of recipes"}
        :post {:handler    (recipe/create-recipe! db)
               :middleware [[(mw/wrap-with-permission :create/recipe)]]
               :parameters {:body create-recipe}
               :responses  {201 {:body create-response}}
               :summary    "Create recipe"}}]
   ["/:recipe-id" {:parameters {:path [:map [:recipe-id :uuid]]}}
    ["" {:get    {:handler    (recipe/retrieve-recipe! db)
                  :parameters {:query get-recipe-query}
                  :responses  {200 {:body schema/recipe}}
                  :summary    "Retrieve recipe"}
         :put    {:handler    (recipe/update-recipe! db)
                  :middleware [[(mw/wrap-with-permission :edit/recipe)]]
                  :parameters {:body create-recipe}
                  :responses  {204 {:body :nil}}
                  :summary    "Update recipe"}
         :delete {:handler    (recipe/delete-recipe! db)
                  :middleware [[(mw/wrap-with-permission :delete/recipe)]]
                  :responses  {204 {:body :nil}}
                  :summary    "Delete recipe"}}]
    ["/bom" {:get {:handler    (recipe/retrieve-recipe-bom! db)
                   :parameters {:query get-recipe-query}
                   :responses  {200 {:body [:vector grocery]}}
                   :summary    "Retrieve recipe bom"}}]
    ["/ingredients" {:middleware [[(mw/wrap-with-permission :edit/recipe)]]}
     ["" {:post {:handler    (recipe/create-ingredient! db)
                 :parameters {:body create-ingredient}
                 :responses  {201 {:body create-response}}
                 :summary    "Create ingredient"}}]
     ["/:ingredient-id"
      {:parameters {:path [:map [:ingredient-id :uuid]]}
       :put        {:handler    (recipe/update-ingredient! db)
                    :parameters {:body create-ingredient}
                    :responses  {204 {:body :nil}}
                    :summary    "Update ingredient"}
       :delete     {:handler   (recipe/delete-ingredient! db)
                    :responses {204 {:body :nil}}
                    :summary   "delete ingredient"}}]]]])
