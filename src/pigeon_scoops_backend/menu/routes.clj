(ns pigeon-scoops-backend.menu.routes
  (:require [pigeon-scoops-backend.schema :refer [create-response]]
            [pigeon-scoops-backend.menu.handlers :as menu]
            [pigeon-scoops-backend.menu.schema :as schema]
            [pigeon-scoops-backend.middleware :as mw]))

(defn routes [{db :jdbc-url}]
  ["/menus" {:openapi {:tags ["menus"]}}
   ["" {:get  {:handler    (menu/list-all-menus! db)
               :parameters {:query schema/get-menus-query}
               :responses  {200 {:body [:vector schema/menu]}}
               :summary    "list of menus"}
        :post {:handler    (menu/create-menu! db)
               :middleware [[(mw/wrap-with-permission :create/menu)]]
               :parameters {:body schema/create-menu}
               :responses  {201 {:body create-response}}
               :summary    "create menu"}}]
   ["/:menu-id" {:parameters {:path [:map [:menu-id :uuid]]}}
    ["" {:get    {:handler   (menu/retrieve-menu! db)
                  :responses {200 {:body schema/menu}}
                  :summary   "retrieve menu"}
         :put    {:handler    (menu/update-menu! db)
                  :middleware [[(mw/wrap-with-permission :edit/menu)]]
                  :parameters {:body schema/create-menu}
                  :responses  {201 {:body create-response}}
                  :summary    "Create menu"}
         :delete {:handler    (menu/delete-menu! db)
                  :middleware [[(mw/wrap-with-permission :delete/menu)]]
                  :responses  {204 {:body :nil}}
                  :summary    "Delete menu"}}]
    ["/items" {:middleware [[(mw/wrap-with-permission :edit/menu)]]}
     ["" {:post {:handler    (menu/create-menu-item! db)
                 :parameters {:body schema/create-menu-item}
                 :responses  {201 {:body create-response}}
                 :summary    "Create menu-item"}}]
     ["/:menu-item-id" {:parameters {:path [:map [:menu-item-id :uuid]]}}
      ["" {:put    {:handler    (menu/update-menu-item! db)
                    :parameters {:body schema/menu-item}
                    :responses  {204 {:body :nil}}
                    :summary    "Update menu-item"}
           :delete {:handler   (menu/delete-menu-item! db)
                    :responses {204 {:body :nil}}
                    :summary   "delete menu-item"}}]
      ["/sizes" {:middleware [[(mw/wrap-with-permission :edit/menu)]]}
       ["" {:post {:handler    (menu/create-menu-item-size! db)
                   :parameters {:body schema/create-menu-item-size}
                   :responses  {201 {:body create-response}}
                   :summary    "Create menu-item size"}}]
       ["/:menu-item-size-id"
        {:parameters {:path [:map [:menu-item-size-id :uuid]]}
         :put        {:handler    (menu/update-menu-item-size! db)
                      :parameters {:body schema/create-menu-item-size}
                      :responses  {204 {:body :nil}}
                      :summary    "Update menu-item size"}
         :delete     {:handler   (menu/delete-menu-item-size! db)
                      :responses {204 {:body :nil}}
                      :summary   "delete menu-item size"}}]]]]]])
