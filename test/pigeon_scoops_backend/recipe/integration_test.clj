(ns pigeon-scoops-backend.recipe.integration-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [integrant.repl.state]
            [pigeon-scoops-backend.test-system :as ts]))

(use-fixtures
  :once
  ts/system-fixture
  (ts/make-account-fixture)
  (ts/make-roles-fixture :manage-recipes :manage-groceries))

(def grocery
  {:grocery/name "orange" :grocery/department :department/produce})

(def recipe
  {:recipe/name         "a spicy meatball"
   :recipe/amount       3
   :recipe/amount-unit  :mass/lb
   :recipe/source       "the book"
   :recipe/instructions ["make them"]})

(def updated-recipe
  (assoc recipe :recipe/source "a scroll"))

(def ingredient
  {:ingredient/amount      1
   :ingredient/amount-unit :volume/floz})

(deftest recipes-list-test
  (testing "List recipes"
    (ts/test-endpoint :post "/v1/recipes" {:use-auth? true :body recipe})
    (testing "with auth"
      (let [{:keys [body]} (ts/test-endpoint :get "/v1/recipes" {:use-auth? true})]
        (is (vector? body))
        (is (seq body))))
    (testing "without auth"
      (let [{:keys [body]} (ts/test-endpoint :get "/v1/recipes" {:use-auth? false})]
        (is (vector? body))
        (is (seq body))))))

(deftest recipes-crud-test
  (let [recipe-id     (atom nil)
        ingredient-id (atom nil)
        grocery-id    (get-in (ts/test-endpoint :post "/v1/groceries" {:use-auth? true :body grocery}) [:body :id])]
    (testing "create recipe"
      (let [{:keys [body]} (ts/test-endpoint :post "/v1/recipes" {:use-auth? true :body recipe})]
        (reset! recipe-id (:id body))))
    (testing "update recipe"
      (ts/test-endpoint :put (str "/v1/recipes/" @recipe-id) {:use-auth? true :body updated-recipe}))
    (testing "create ingredient from recipe"
      (let [ingredient     (assoc ingredient :ingredient/ingredient-recipe-id @recipe-id)
            {:keys [body]} (ts/test-endpoint :post (str "/v1/recipes/" @recipe-id "/ingredients") {:use-auth? true :body ingredient})]
        (reset! ingredient-id (:id body))))
    (testing "update ingredient"
      (ts/test-endpoint :put (str "/v1/recipes/" @recipe-id "/ingredients/" @ingredient-id)
                        {:use-auth? true :body (assoc ingredient
                                                      :ingredient/amount 3000
                                                      :ingredient/ingredient-recipe-id @recipe-id)}))
    (testing "switching from recipe to grocery ingredient"
      (let [ingredient (-> ingredient
                           (assoc :ingredient/ingredient-grocery-id grocery-id)
                           (dissoc :ingredient/ingredient-recipe-id))]
        (ts/test-endpoint :put (str "/v1/recipes/" @recipe-id "/ingredients/" @ingredient-id) {:use-auth? true :body ingredient})))
    (testing "create ingredient from grocery"
      (let [ingredient (assoc ingredient :ingredient/ingredient-grocery-id grocery-id)]
        (ts/test-endpoint :post (str "/v1/recipes/" @recipe-id "/ingredients") {:use-auth? true :body ingredient})))
    (testing "retrieve recipe"
      (ts/test-endpoint :get (str "/v1/recipes/" @recipe-id) {:use-auth? true}))
    (testing "retrieve scaled"
      (ts/test-endpoint :get (str "/v1/recipes/" @recipe-id) {:use-auth? true :params {:amount 5 :amount-unit "mass/g"}}))
    (testing "retrieve bom"
      (ts/test-endpoint :get (str "/v1/recipes/" @recipe-id "/bom") {:use-auth? true :params {:amount 5 :amount-unit "mass/g"}}))
    (testing "delete ingredient"
      (ts/test-endpoint :delete (str "/v1/recipes/" @recipe-id "/ingredients/" @ingredient-id) {:use-auth? true}))
    (testing "delete recipe"
      (ts/test-endpoint :delete (str "/v1/recipes/" @recipe-id) {:use-auth? true}))))
