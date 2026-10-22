(ns pigeon-scoops-backend.grocery.routes
  (:require [pigeon-scoops-backend.schema :refer [create-response sub-schema]]
            [pigeon-scoops-backend.grocery.handlers :as grocery]
            [pigeon-scoops-backend.grocery.schema :as schema]
            [pigeon-scoops-backend.middleware :as mw]))

(def public-keys [:grocery/id :grocery/department :grocery/name])

(def create-grocery
  (sub-schema schema/grocery [:grocery/name
                              :grocery/department]))

(def create-grocery-unit
  (sub-schema schema/grocery-unit [:grocery-unit/source
                                   :grocery-unit/unit-cost
                                   :grocery-unit/unit-common
                                   :grocery-unit/unit-common-type
                                   :grocery-unit/unit-mass
                                   :grocery-unit/unit-mass-type
                                   :grocery-unit/unit-volume
                                   :grocery-unit/unit-volume-type]))

(defn routes [{db :jdbc-url}]
  ["/groceries" {:openapi {:tags ["groceries"]}}
   ["" {:get  {:handler    (grocery/list-all-groceries! db)
               :middleware [[(mw/wrap-with-permission :view/grocery public-keys)]]
               :responses  {200 {:body [:vector schema/grocery]}}
               :summary    "list of groceries"}
        :post {:handler    (grocery/create-grocery! db)
               :middleware [[(mw/wrap-with-permission :create/grocery)]]
               :parameters {:body create-grocery}
               :responses  {201 {:body create-response}}
               :summary    "create a grocery"}}]
   ["/:grocery-id" {:parameters {:path [:map [:grocery-id :uuid]]}}
    ["" {:get    {:handler    (grocery/retrieve-grocery! db)
                  :middleware [[(mw/wrap-with-permission :view/grocery public-keys)]]
                  :responses  {200 {:body schema/grocery}}
                  :summary    "Retrieve grocery"}
         :put    {:handler    (grocery/update-grocery! db)
                  :middleware [[(mw/wrap-with-permission :edit/grocery)]]
                  :parameters {:body create-grocery}
                  :responses  {204 {:body :nil}}
                  :summary    "Update grocery"}
         :delete {:handler    (grocery/delete-grocery! db)
                  :middleware [[(mw/wrap-with-permission :delete/grocery)]]
                  :responses  {204 {:body :nil}}
                  :summary    "Delete grocery"}}]
    ["/units" {:middleware [[(mw/wrap-with-permission :edit/grocery)]]}
     ["" {:post {:handler    (grocery/create-grocery-unit! db)
                 :parameters {:body create-grocery-unit}
                 :responses  {201 {:body create-response}}
                 :summary    "Create grocery-unit"}}]
     ["/:unit-id" {:parameters {:path [:map [:unit-id :uuid]]}
                   :put        {:handler    (grocery/update-grocery-unit! db)
                                :parameters {:body create-grocery-unit}
                                :responses  {204 {:body :nil}}
                                :summary    "Update grocery-unit"}
                   :delete     {:handler   (grocery/delete-grocery-unit! db)
                                :responses {204 {:body :nil}}
                                :summary   "delete grocery-unit"}}]]]])
