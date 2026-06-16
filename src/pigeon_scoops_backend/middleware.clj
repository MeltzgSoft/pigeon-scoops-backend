(ns pigeon-scoops-backend.middleware
  (:require [clojure.string :as str]
            [pigeon-scoops-backend.utils :refer [doall-deep remove-nil-keys]]
            [ring.middleware.jwt :as jwt]
            [ring.util.response :as rr]))

(defn wrap-auth0 [jwt-config]
  {:name        ::auth0
   :description "Middleware for auth0 authentication and authorization"
   :wrap        (fn [handler]
                  (jwt/wrap-jwt handler jwt-config))})

(def wrap-remove-nil-keys
  {:name        ::remove-nil-keys
   :description "Middleware to recursively remove keys with nil values from response body"
   :wrap        (fn [handler]
                  (fn [request]
                    (-> request
                        (handler)
                        (update :body (comp doall-deep remove-nil-keys)))))})

(defn wrap-owner
  ([id-key table find-by-id]
   (wrap-owner id-key table find-by-id nil?))
  ([id-key table find-by-id allow-admin-access-fn]
   {:name        (keyword (str *ns*) (str (name table)))
    :description (str "Middleware to check if a request user is an" table " owner")
    :wrap        (fn [handler db]
                   (fn [request]
                     (let [uid (-> request :claims :sub)
                           entity-id (-> request :parameters :path id-key)
                           entity (find-by-id db entity-id)]
                       (if (or (= ((keyword (name table) "user-id") entity) uid)
                               (allow-admin-access-fn request))
                         (handler request)
                         (-> (rr/response {:message (str "Operation requires " (name table) " ownership")
                                           :data    (str "entity-id " entity-id)
                                           :type    :authorization-required})
                             (rr/status 401))))))}))

(defn wrap-with-permission
  ([permission]
   (wrap-with-permission permission nil))
  ([permissions public-keys]
   {:name        (keyword (str *ns*) (str permissions))
    :description (str "Middleware to check if a user has the " permissions " permission")
    :wrap        (fn [handler]
                   (fn [request]
                     (let [expected-permissions (if (keyword? permissions)
                                                  [permissions]
                                                  permissions)
                           user-permissions (set (map #(keyword (str/replace % ":" "/"))
                                                      (get-in request [:claims "https://api.pigeon-scoops.com/perms"])))]
                       (cond
                         (seq (remove nil? (map #(user-permissions %) expected-permissions)))
                         (handler request)
                         (seq public-keys)
                         (let [response (handler request)]
                           (update
                            response
                            :body
                            #(cond
                               (map? %)
                               (select-keys % public-keys)
                               (or (coll? %) (seq? %))
                               (map (fn [elem] (select-keys elem public-keys)) %)
                               :else
                               (-> (rr/response {:message (str "Could not extract public keys. Operation requires "
                                                               expected-permissions
                                                               " permission")
                                                 :data    (:uri request)
                                                 :type    :authorization-required})
                                   (rr/status 401)))))

                         :else
                         (-> (rr/response {:message (str "Operation requires an of " permissions " permission")
                                           :data    (:uri request)
                                           :type    :authorization-required})
                             (rr/status 401))))))}))

