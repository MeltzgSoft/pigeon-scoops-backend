(ns pigeon-scoops-backend.ui
  (:require [hiccup2.core :as h]
            [ring.util.response :as rr]))

(defn template []
  (str "<!DOCTYPE html>"
       (h/html [:html
                [:head
                 [:script {:src   "/webjars/htmx.org/4.0.0/dist/htmx.min.js"
                           :defer true}]
                 [:link {:rel  "stylesheet"
                         :href "/css/site.css"}]
                 [:title "Example"]]
                [:body
                 [:div
                  [:p "This is some text"]]]])))

(defn routes [_]
  ["" {:get {:no-doc  true
             :handler (constantly
                       (rr/response (template)))}}])

