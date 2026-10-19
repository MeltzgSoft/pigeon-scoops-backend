(ns pigeon-scoops-backend.recipe.schema
  (:require [pigeon-scoops-backend.units.common :as common]))

(def ingredient
  [:map
   [:ingredient/id :uuid]
   [:ingredient/recipe-id :uuid]
   [:recipe/name {:optional true} :string]
   [:grocery/name {:optional true} :string]
   [:ingredient/ingredient-grocery-id {:optional true} :uuid]
   [:ingredient/ingredient-recipe-id {:optional true} :uuid]
   [:ingredient/amount {:min 0} :double]
   [:ingredient/amount-unit (vec (concat [:enum] common/all-units))]])

(def recipe
  [:map
   [:recipe/id :uuid]
   [:recipe/name :string]
   [:recipe/is-mystery :boolean]
   [:recipe/amount {:min 0} :double]
   [:recipe/amount-unit (vec (concat [:enum] common/all-units))]
   [:recipe/source :string]
   [:recipe/instructions {:optional true} [:vector :string]]
   [:recipe/ingredients {:optional true} [:vector ingredient]]
   [:recipe/description {:optional true} :string]
   [:recipe/mystery-description {:optional true} :string]])
