(ns pigeon-scoops-backend.recipe.schema
  (:require [clojure.spec.alpha :as s]
            [pigeon-scoops-backend.units.common :as common]
            [pigeon-scoops-backend.units.mass :as mass]
            [pigeon-scoops-backend.units.volume :as volume]
            [spec-tools.data-spec :as ds]))

(def ingredient
  {:ingredient/id                             uuid?
   :ingredient/recipe-id                      uuid?
   (ds/opt :recipe/name)                      string?
   (ds/opt :grocery/name)                     string?
   (ds/opt :ingredient/ingredient-grocery-id) uuid?
   (ds/opt :ingredient/ingredient-recipe-id)  uuid?
   :ingredient/amount                         number?
   :ingredient/amount-unit                    (s/and keyword?
                                                     (set (concat common/other-units
                                                                  (keys mass/conversion-map)
                                                                  (keys volume/conversion-map))))})

(def recipe
  {:recipe/id                           uuid?
   :recipe/name                         string?
   :recipe/is-mystery                   boolean?
   :recipe/amount                       number?
   :recipe/amount-unit                  (s/and keyword?
                                               (set (concat common/other-units
                                                            (keys mass/conversion-map)
                                                            (keys volume/conversion-map))))
   :recipe/source                       string?
   (ds/opt :recipe/instructions)        [string?]
   (ds/opt :recipe/ingredients)         [ingredient]
   (ds/opt :recipe/description)         string?
   (ds/opt :recipe/mystery-description) string?})
