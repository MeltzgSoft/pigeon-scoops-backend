(ns pigeon-scoops-backend.recipe.routes
  (:require [pigeon-scoops-backend.schema :refer [create-response]]
            [pigeon-scoops-backend.grocery.schema :as grocery-responses]
            [pigeon-scoops-backend.middleware :as mw]
            [pigeon-scoops-backend.recipe.handlers :as recipe]
            [pigeon-scoops-backend.recipe.schema :as schema]))

(defn routes [{db :jdbc-url}]
  ["/recipes" {:openapi {:tags ["recipes"]}}
   ["" {:get  {:handler   (recipe/list-all-recipes! db)
               :responses {200 {:body [:vector schema/recipe]}}
               :summary   "list of recipes"}
        :post {:handler    (recipe/create-recipe! db)
               :middleware [[(mw/wrap-with-permission :create/recipe)]]
               :parameters {:body schema/create-recipe}
               :responses  {201 {:body create-response}}
               :summary    "Create recipe"}}]
   ["/:recipe-id" {:parameters {:path [:map [:recipe-id :uuid]]}}
    ["" {:get    {:handler    (recipe/retrieve-recipe! db)
                  :parameters {:query schema/get-recipe-query}
                  :responses  {200 {:body schema/recipe}}
                  :summary    "Retrieve recipe"}
         :put    {:handler    (recipe/update-recipe! db)
                  :middleware [[(mw/wrap-with-permission :edit/recipe)]]
                  :parameters {:body schema/create-recipe}
                  :responses  {204 {:body :nil}}
                  :summary    "Update recipe"}
         :delete {:handler    (recipe/delete-recipe! db)
                  :middleware [[(mw/wrap-with-permission :delete/recipe)]]
                  :response   {204 {:body :nil}}
                  :summary    "Delete recipe"}}]
    ["/bom" {:get {:handler    (recipe/retrieve-recipe-bom! db)
                   :parameters {:query schema/get-recipe-query}
                   :responses  {200 {:body [:vector grocery-responses/grocery]}}
                   :summary    "Retrieve recipe bom"}}]
    ["/ingredients" {:middleware [[(mw/wrap-with-permission :edit/recipe)]]}
     ["" {:post {:handler    (recipe/create-ingredient! db)
                 :parameters {:body schema/create-ingredient}
                 :responses  {201 {:body create-response}}
                 :summary    "Create ingredient"}}]
     ["/:ingredient-id"
      {:parameters {:path [:map [:ingredient-id :uuid]]}
       :put        {:handler    (recipe/update-ingredient! db)
                    :parameters {:body schema/create-ingredient}
                    :responses  {204 {:body :nil}}
                    :summary    "Update ingredient"}
       :delete     {:handler   (recipe/delete-ingredient! db)
                    :responses {204 {:body :nil}}
                    :summary   "delete ingredient"}}]]]])
