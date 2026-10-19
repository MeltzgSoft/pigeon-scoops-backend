(ns pigeon-scoops-backend.account.handlers
  (:require [pigeon-scoops-backend.account.db :as account-db]
            [pigeon-scoops-backend.auth :as auth0]
            [pigeon-scoops-backend.utils :refer [with-connection!]]
            [ring.util.response :as rr]
            [ring.util.codec :refer [form-decode]]))

(defn get-accounts! [auth db]
  (fn [_]
    (with-connection!
      db
      (fn [db]
        (let [accounts    (account-db/find-all-accounts! db)
              roles->uids (update-vals (auth0/get-roles->uids! auth) set)]
          (rr/response
           (map (fn [acct]
                  (assoc acct :account/roles
                         (remove nil?
                                 (map (fn [[role uids]]
                                        (when (uids (:account/id acct))
                                          role))
                                      roles->uids))))
                accounts)))))))

(defn create-account! [db]
  (fn [request]
    (with-connection!
      db
      (fn [db]
        (let [{:keys [sub picture] :as claims} (:claims request)
              existing-account                 (account-db/find-account-by-id! db sub)]
          (if existing-account
            (rr/status 204)
            (do
              (account-db/create-account! db {:id sub :name (get claims "https://api.pigeon-scoops.com/email") :picture picture})
              (rr/status 201))))))))

(defn delete-account! [auth db]
  (fn [request]
    (let [uid                   (-> request :claims :sub)
          delete-auth0-account! (auth0/delete-user! auth uid)]
      (if (= (:status delete-auth0-account!) 204)
        (do
          (account-db/delete-account! db {:id uid})
          (rr/status 204))
        (rr/not-found {:type    "user-not-found"
                       :message "User not found"
                       :data    (str "uid " uid)})))))

(defn update-roles! [auth]
  (fn [request]
    (let [uid   (-> request :parameters :path :user-id form-decode)
          roles (-> request :parameters :body :roles)]
      (if (auth0/update-roles! auth uid roles)
        (rr/status 204)
        (rr/status 500)))))
