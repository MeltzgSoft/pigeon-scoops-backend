(ns pigeon-scoops-backend.test-system
  (:require [buddy.sign.jwt :as jwt-sign]
            [clojure.test :refer [do-report]]
            [environ.core :refer [env]]
            [integrant.core :as ig]
            [integrant.repl :as ig-repl]
            [integrant.repl.state :as state]
            [muuntaja.core :as m]
            [pigeon-scoops-backend.auth :as auth0]
            [pigeon-scoops-backend.utils :refer [load-config! init-system!]]
            [pigeon-scoops-backend.db-tasks]
            [ring.mock.request :as mock]
            [pigeon-scoops-backend.server])
  (:import (java.net Socket)
           (java.security KeyPairGenerator)
           (java.util Date UUID)))

(def ^:private test-key-pair
  (let [gen (KeyPairGenerator/getInstance "RSA")]
    (.initialize gen 2048)
    (.generateKeyPair gen)))

(def tokens (atom nil))
(def test-users (atom nil))
(def ^:private test-roles (atom {}))

;; Maps Auth0 role names to permissions using "action:resource" convention.
;; The JWT middleware converts "action:resource" → :action/resource for permission checks.
(def ^:private role->permissions
  {:manage-roles      ["edit:role"]
   :manage-groceries  ["view:grocery" "create:grocery" "edit:grocery" "delete:grocery"]
   :manage-recipes    ["create:recipe" "edit:recipe" "delete:recipe"]
   :manage-menus      ["create:menu" "edit:menu" "delete:menu"]
   :manage-orders     ["create:order" "edit:order"]
   :manage-production ["manage:production"]})

(defn- make-test-token [{:keys [uid username]}]
  (let [perms (->> (get @test-roles uid #{})
                   (mapcat role->permissions)
                   distinct
                   vec)]
    (jwt-sign/sign
     {:sub                                  uid
      :iss                                  "https://pigeon-scoops.us.auth0.com/"
      :exp                                  (Date. ^long (+ (System/currentTimeMillis) (* 3600 1000)))
      "https://api.pigeon-scoops.com/email" username
      "https://api.pigeon-scoops.com/perms" perms}
     (.getPrivate test-key-pair)
     {:alg :rs256})))

(defn- local-update-roles! [uid roles]
  (swap! test-roles assoc uid (set roles))
  true)

(defn- local-get-roles->uids! []
  (reduce-kv (fn [m uid roles]
               (reduce (fn [acc role] (update acc role (fnil conj #{}) uid))
                       m
                       roles))
             {}
             @test-roles))

(defn- local-jwt-config []
  {:issuers {"https://pigeon-scoops.us.auth0.com/"
             {:alg        :RS256
              :public-key (.getPublic test-key-pair)}}})

(defn test-endpoint
  ([method uri]
   (test-endpoint method uri nil))
  ([method uri {:keys [use-other-user params use-auth? body retry-status]}]
   (let [app   (-> state/system :server/routes)
         user  (if use-other-user (second @test-users) (first @test-users))
         token (or (if use-other-user (second @tokens) (first @tokens))
                   (make-test-token user))
         request (-> (mock/request method uri params)
                     (cond-> use-auth? (mock/header :authorization (str "Bearer " token))
                             body (mock/json-body body)))
         response (loop [resp (app request)]
                    (if (or (not retry-status)
                            (not= (:status resp) retry-status))
                      resp
                      (do
                        (println "request failed. retrying" resp)
                        (recur (app request)))))
         response (update response :body #(try
                                            (m/decode "application/json" %)
                                            (catch Exception e
                                              (println "FAILED TO DECODE" % "RESPONSE" response)
                                              (throw e))))]
     (println method uri response)
     response)))

(defn port-available? [port]
  (try
    (let [^String host "localhost"
          ^Integer port port]
      (.close (Socket. host port)))
    false
    (catch Exception _
      true)))

(defn find-next-available-port [ports]
  (first (filter port-available? ports)))

(defn strict-fixture [{:keys [setup teardown msg]}]
  (fn [f]
    (try
      (when setup (setup))
      (try
        (f)
        (finally
          (when teardown (teardown))))
      (catch Throwable t
        (do-report {:type :error :message msg :expected nil :actual t})
        (throw t)))))

(def system-fixture
  (let [postgres-container (atom nil)]
    (strict-fixture
     {:msg "failed to set up test system"
      :setup (when-not state/system
               (fn []
                 (reset! postgres-container
                         (doto (org.testcontainers.containers.PostgreSQLContainer. "postgres:latest")
                           (.withDatabaseName "test_db")
                           (.withUsername "user")
                           (.withPassword "password")
                           (.start)))
                 (let [port     (find-next-available-port (range 3000 4000))
                       full-uri (str (.getJdbcUrl @postgres-container)
                                     "&user=" (.getUsername @postgres-container)
                                     "&password=" (.getPassword @postgres-container))]
                   (ig-repl/set-prep!
                    (fn []
                      (let [task-system (-> "resources/db-task-config.edn"
                                            (load-config!)
                                            (assoc-in [:db/postgres :jdbc-url] full-uri)
                                            (init-system!))
                            migration-task (:db-tasks/migration task-system)]
                        (migration-task)
                        (ig/halt! task-system)
                        (-> (if (env :ci-env)
                              "resources/server-config.edn"
                              "dev/resources/server-config.edn")
                            (load-config!)
                            (assoc-in [:db/postgres :jdbc-url] full-uri)
                            (assoc-in [:server/jetty :port] port)
                            (assoc-in [:auth/auth0 :skip-auth0-delete?] true)
                            (assoc-in [:auth/auth0 :jwt-config] (local-jwt-config))
                            (assoc-in [:auth/auth0 :update-roles-fn] local-update-roles!)
                            (assoc-in [:auth/auth0 :get-roles->uids-fn] local-get-roles->uids!)
                            (ig/expand)))))
                   (ig-repl/go))))
      :teardown (fn []
                  (ig-repl/halt)
                  (.stop @postgres-container))})))

(defn make-account-fixture
  ([]
   (make-account-fixture true))
  ([manage-user?]
   (strict-fixture
    {:setup    (fn []
                 (reset! test-roles {})
                 (let [uids      (repeatedly 2 #(str "local|" (UUID/randomUUID)))
                       usernames (repeatedly 2 #(str "integration-test" (UUID/randomUUID) "@pigeon-scoops.com"))]
                   (reset! test-users (mapv #(hash-map :uid %1 :username %2) uids usernames))
                   (reset! tokens (mapv make-test-token @test-users))
                   (when manage-user?
                     (mapv #(test-endpoint :post "/v1/account" {:use-auth? true :use-other-user %}) [true false]))))
     :teardown (fn []
                 (reset! tokens nil)
                 (reset! test-roles {}))
     :msg      "account fixture failed"})))

(defn make-roles-fixture [& roles]
  (strict-fixture
   {:setup    (fn []
                (let [auth           (:auth/auth0 state/system)
                      roles-per-user (if (keyword? (first roles))
                                       (repeat (count @test-users) roles)
                                       roles)]
                  (doall
                   (map (fn [{:keys [uid]} user-roles]
                          (auth0/update-roles! auth uid user-roles))
                        @test-users
                        roles-per-user))
                  (reset! tokens (mapv make-test-token @test-users))))
    :teardown #(reset! tokens nil)
    :msg      "make roles failed"}))
