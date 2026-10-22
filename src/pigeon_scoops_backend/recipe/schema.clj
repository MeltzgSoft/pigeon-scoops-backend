(ns pigeon-scoops-backend.recipe.schema
  (:require [pigeon-scoops-backend.units.schema :as unit-schema]))

(def ingredient
  [:map
   [:ingredient/id :uuid]
   [:ingredient/recipe-id :uuid]
   [:recipe/name {:optional true} :string]
   [:grocery/name {:optional true} :string]
   [:ingredient/ingredient-grocery-id {:optional true} :uuid]
   [:ingredient/ingredient-recipe-id {:optional true} :uuid]
   [:ingredient/amount [:and :double [:> 0]]]
   [:ingredient/amount-unit unit-schema/all-units-enum]])

(def recipe
  [:map
   [:recipe/id :uuid]
   [:recipe/name :string]
   [:recipe/is-mystery {:optional true} :boolean]
   [:recipe/amount [:and :double [:> 0]]]
   [:recipe/amount-unit unit-schema/all-units-enum]
   [:recipe/source :string]
   [:recipe/instructions {:optional true} [:vector :string]]
   [:recipe/ingredients {:optional true} [:vector ingredient]]
   [:recipe/description {:optional true} :string]
   [:recipe/mystery-description {:optional true} :string]])

