(ns pigeon-scoops-backend.recipe.routes
  (:require [clojure.spec.alpha :as s]
            [pigeon-scoops-backend.grocery.responses :as grocery-responses]
            [pigeon-scoops-backend.middleware :as mw]
            [pigeon-scoops-backend.recipe.handlers :as recipe]
            [pigeon-scoops-backend.recipe.responses :as responses]
            [pigeon-scoops-backend.units.common :as common]
            [pigeon-scoops-backend.units.mass :as mass]
            [pigeon-scoops-backend.units.volume :as volume]
            [spec-tools.data-spec :as ds]))

(defn routes [{db :jdbc-url}]
  ["/recipes" {:openapi    {:tags ["recipes"]}}
   ["" {:get  {:handler   (recipe/list-all-recipes! db)
               :responses {200 {:body [responses/recipe]}}
               :summary   "list of recipes"}
        :post {:handler    (recipe/create-recipe! db)
               :middleware [[(mw/wrap-with-permission :create/recipe)]]
               :parameters {:body {:recipe/name                         string?
                                   (ds/opt :recipe/is-mystery)          boolean?
                                   (ds/opt :recipe/description)         string?
                                   (ds/opt :recipe/mystery-description) string?
                                   :recipe/instructions                 [string?]
                                   :recipe/amount                       number?
                                   :recipe/amount-unit                  (s/and keyword? (set (concat common/other-units
                                                                                                     (keys mass/conversion-map)
                                                                                                     (keys volume/conversion-map))))
                                   :recipe/source                       string?}}
               :responses  {201 {:body {:id uuid?}}}
               :summary    "Create recipe"}}]
   ["/:recipe-id" {:parameters {:path {:recipe-id uuid?}}}
    ["" {:get    {:handler    (recipe/retrieve-recipe! db)
                  :parameters {:query {(ds/opt :amount)      number?
                                       (ds/opt :amount-unit) (s/and keyword? (set (concat common/other-units
                                                                                          (keys mass/conversion-map)
                                                                                          (keys volume/conversion-map))))}}
                  :responses  {200 {:body responses/recipe}}
                  :summary    "Retrieve recipe"}
         :put    {:handler    (recipe/update-recipe! db)
                  :middleware [[(mw/wrap-with-permission :edit/recipe)]]
                  :parameters {:body {:recipe/name                         string?
                                      (ds/opt :recipe/is-mystery)          boolean?
                                      (ds/opt :recipe/description)         string?
                                      (ds/opt :recipe/mystery-description) string?
                                      :recipe/instructions                 [string?]
                                      :recipe/amount                       number?
                                      :recipe/amount-unit                  (s/and keyword? (set (concat common/other-units
                                                                                                        (keys mass/conversion-map)
                                                                                                        (keys volume/conversion-map))))
                                      :recipe/source                       string?}}
                  :responses  {204 {:body nil?}}
                  :summary    "Update recipe"}
         :delete {:handler    (recipe/delete-recipe! db)
                  :middleware [[(mw/wrap-with-permission :delete/recipe)]]
                  :response   {204 {:body nil?}}
                  :summary    "Delete recipe"}}]
    ["/bom" {:get {:handler    (recipe/retrieve-recipe-bom! db)
                   :parameters {:query {:amount      number?
                                        :amount-unit (s/and keyword? (set (concat common/other-units
                                                                                  (keys mass/conversion-map)
                                                                                  (keys volume/conversion-map))))}}
                   :responses  {200 {:body [grocery-responses/grocery]}}
                   :summary    "Retrieve recipe bom"}}]
    ["/ingredients" {:middleware [[(mw/wrap-with-permission :edit/recipe)]]}
     ["" {:post   {:handler    (recipe/create-ingredient! db)
                   :parameters {:body {(ds/opt :ingredient/ingredient-grocery-id) uuid?
                                       (ds/opt :ingredient/ingredient-recipe-id)  uuid?
                                       :ingredient/amount                         number?
                                       :ingredient/amount-unit                    (s/and keyword? (set (concat common/other-units
                                                                                                               (keys mass/conversion-map)
                                                                                                               (keys volume/conversion-map))))}}
                   :responses  {201 {:body {:id uuid?}}}
                   :summary    "Create ingredient"}}]
     ["/:ingredient-id"
      {:parameters {:path {:ingredient-id uuid?}}
       :put    {:handler    (recipe/update-ingredient! db)
                :parameters {:body {(ds/opt :ingredient/ingredient-grocery-id) uuid?
                                    (ds/opt :ingredient/ingredient-recipe-id)  uuid?
                                    :ingredient/amount                         number?
                                    :ingredient/amount-unit                    (s/and keyword? (set (concat common/other-units
                                                                                                            (keys mass/conversion-map)
                                                                                                            (keys volume/conversion-map))))}}
                :responses  {204 {:body nil?}}
                :summary    "Update ingredient"}
       :delete {:handler    (recipe/delete-ingredient! db)
                :responses  {204 {:body nil?}}
                :summary    "delete ingredient"}}]]]])
