(ns pigeon-scoops-backend.auth
  (:require [clojure.set :as set]
            [environ.core :refer [env]]
            [clj-http.client :as http]
            [integrant.core :as ig]
            [muuntaja.core :as m]))

(def roles #{:manage-roles
             :manage-recipes
             :manage-groceries
             :manage-orders
             :manage-menus
             :manage-production})

(defmethod ig/expand-key :auth/auth0 [k config]
  {k (merge config (cond-> {}
                     (env :test-client-id) (conj {:test-client-id (env :test-client-id)})
                     (env :management-client-id) (conj {:management-client-id (env :management-client-id)})
                     (env :management-client-secret) (conj {:management-client-secret (env :management-client-secret)})))})

(defmethod ig/init-key :auth/auth0 [_ config]
  config)

(defn get-management-token! [{:keys [management-client-id management-client-secret]}]
  (->> {:content-type  :json
        :cookie-policy :standard
        :body          (m/encode "application/json"
                                 {:client_id     management-client-id
                                  :client_secret management-client-secret
                                  :audience      "https://pigeon-scoops.us.auth0.com/api/v2/"
                                  :grant_type    "client_credentials"})}
       (http/post "https://pigeon-scoops.us.auth0.com/oauth/token")
       (m/decode-response-body)
       :access_token))

(defn delete-user! [{:keys [skip-auth0-delete?] :as auth} uid]
  (if skip-auth0-delete?
    {:status 204}
    (->> {:headers {"Authorization" (str "Bearer " (get-management-token! auth))}}
         (http/delete
          (str "https://pigeon-scoops.us.auth0.com/api/v2/users/" uid)))))

(defn create-user! [auth {:keys [connection email password]}]
  (let [token (get-management-token! auth)]
    (->> {:headers          {"Authorization" (str "Bearer " token)}
          :throw-exceptions false
          :cookie-policy    :standard
          :content-type     :json
          :body             (m/encode "application/json"
                                      {:connection connection
                                       :email      email
                                       :password   password})}
         (http/post "https://pigeon-scoops.us.auth0.com/api/v2/users")
         (m/decode-response-body))))

(defn get-roles! [token]
  (->> {:headers       {"Authorization" (str "Bearer " token)}
        :cookie-policy :standard
        :content-type  :json}
       (http/get "https://pigeon-scoops.us.auth0.com/api/v2/roles")
       (m/decode-response-body)))

(defn get-role-ids! [token role-names]
  (->> (get-roles! token)
       (filter #((set role-names) (:name %)))
       (map :id)))

(defn get-users! [auth]
  (->> {:headers {"Authorization" (str "Bearer " (get-management-token! auth))}}
       (http/get "https://pigeon-scoops.us.auth0.com/api/v2/users")
       (m/decode-response-body)))

(defn get-roles->uids! [{:keys [get-roles->uids-fn] :as auth}]
  (if get-roles->uids-fn
    (get-roles->uids-fn)
    (let [token (get-management-token! auth)
          roles (get-roles! token)]
      (->> roles
           (map #(vector (-> % :name keyword)
                         (->> {:headers {"Authorization" (str "Bearer " token)}
                               :cookie-policy :standard
                               :content-type :json}
                              (http/get (str "https://pigeon-scoops.us.auth0.com/api/v2/roles/" (:id %) "/users"))
                              (m/decode-response-body)
                              (map :user_id))))
           (into {})))))

(defn update-roles! [{:keys [update-roles-fn] :as auth} uid roles]
  (if update-roles-fn
    (update-roles-fn uid roles)
    (let [token (get-management-token! auth)
          current-roles (->> {:headers {"Authorization" (str "Bearer " token)}
                              :cookie-policy :standard
                              :content-type :json}
                             (http/get (str "https://pigeon-scoops.us.auth0.com/api/v2/users/" uid "/roles"))
                             (m/decode-response-body)
                             (map (comp keyword :name)))
          to-delete (set/difference (set current-roles)
                                    (set roles))
          to-add (set/difference (set roles)
                                 (set current-roles))]
      (every? true? (map (fn [[method roles]]
                           (if (seq roles)
                             (->> {:headers          {"Authorization" (str "Bearer " token)}
                                   :cookie-policy    :standard
                                   :content-type     :json
                                   :throw-exceptions false
                                   :body             (m/encode "application/json"
                                                               {:roles (get-role-ids! token (map name roles))})}
                                  (method (str "https://pigeon-scoops.us.auth0.com/api/v2/users/" uid "/roles"))
                                  :status
                                  (= 204))
                             true))
                         {http/delete to-delete
                          http/post to-add})))))
