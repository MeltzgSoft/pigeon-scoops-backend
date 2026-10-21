(ns pigeon-scoops-backend.menu.routes
  (:require [pigeon-scoops-backend.schema :refer [create-response]]
            [pigeon-scoops-backend.menu.handlers :as menu]
            [pigeon-scoops-backend.menu.schema :as schema]
            [pigeon-scoops-backend.middleware :as mw]))

(defn routes [{db :jdbc-url}]
  ["/menus" {:openapi {:tags ["menus"]}}
   ["" {:get  {:handler    (menu/list-all-menus! db)
               :parameters {:query {(ds/opt :include-inactive) boolean?
                                    (ds/opt :detailed)         boolean?}}
               :responses  {200 {:body [schema/menu]}}
               :summary    "list of menus"}
        :post {:handler    (menu/create-menu! db)
               :middleware [[(mw/wrap-with-permission :create/menu)]]
               :parameters {:body {:menu/name             string?
                                   (ds/opt :menu/repeats) boolean?
                                   (ds/opt :menu/active)  boolean?
                                   :menu/duration         number?
                                   :menu/duration-type    (s/and keyword?
                                                                 schema/durations)}}
               :responses  {201 {:body create-response}}
               :summary    "create menu"}}]
   ["/:menu-id" {:parameters {:path [:map [:menu-id :uuid]]}}
    ["" {:get    {:handler   (menu/retrieve-menu! db)
                  :responses {200 {:body schema/menu}}
                  :summary   "retrieve menu"}
         :put    {:handler    (menu/update-menu! db)
                  :middleware [[(mw/wrap-with-permission :edit/menu)]]
                  :parameters {:body {:menu/name          string?
                                      :menu/repeats       boolean?
                                      :menu/active        boolean?
                                      :menu/duration      number?
                                      :menu/duration-type (s/and keyword?
                                                                 schema/durations)}}
                  :responses  {201 {:body create-response}}
                  :summary    "Create menu"}
         :delete {:handler    (menu/delete-menu! db)
                  :middleware [[(mw/wrap-with-permission :delete/menu)]]
                  :responses  {204 {:body :nil}}
                  :summary    "Delete menu"}}]
    ["/items" {:middleware [[(mw/wrap-with-permission :edit/menu)]]}
     ["" {:post {:handler    (menu/create-menu-item! db)
                 :parameters {:body {:menu-item/recipe-id uuid?}}
                 :responses  {201 {:body create-response}}
                 :summary    "Create menu-item"}}]
     ["/:menu-item-id" {:parameters {:path [:map [:menu-item-id :uuid]]}}
      ["" {:put    {:handler    (menu/update-menu-item! db)
                    :parameters {:body {:menu-item/recipe-id uuid?}}
                    :responses  {204 {:body :nil}}
                    :summary    "Update menu-item"}
           :delete {:handler   (menu/delete-menu-item! db)
                    :responses {204 {:body :nil}}
                    :summary   "delete menu-item"}}]
      ["/sizes" {:middleware [[(mw/wrap-with-permission :edit/menu)]]}
       ["" {:post {:handler    (menu/create-menu-item-size! db)
                   :parameters {:body {:menu-item-size/amount                      number?
                                       :menu-item-size/amount-unit                 (s/and keyword? (set (concat common/other-units
                                                                                                                (keys mass/conversion-map)
                                                                                                                (keys volume/conversion-map))))
                                       (ds/opt :menu-item-size/available-quantity) integer?}}
                   :responses  {201 {:body create-response}}
                   :summary    "Create menu-item size"}}]
       ["/:menu-item-size-id"
        {:parameters {:path [:map [:menu-item-size-id :uuid]]}
         :put        {:handler    (menu/update-menu-item-size! db)
                      :parameters {:body {:menu-item-size/amount                      number?
                                          :menu-item-size/amount-unit                 (s/and keyword? (set (concat common/other-units
                                                                                                                   (keys mass/conversion-map)
                                                                                                                   (keys volume/conversion-map))))
                                          (ds/opt :menu-item-size/available-quantity) integer?}}
                      :responses  {204 {:body :nil}}
                      :summary    "Update menu-item size"}
         :delete     {:handler   (menu/delete-menu-item-size! db)
                      :responses {204 {:body :nil}}
                      :summary   "delete menu-item size"}}]]]]]])
