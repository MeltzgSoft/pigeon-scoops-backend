(ns pigeon-scoops-backend.menu.db
  (:require [honey.sql :as hsql]
            [honey.sql.helpers :as h]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [pigeon-scoops-backend.utils :refer [apply-db-str->keyword
                                                 apply-keyword->db-str
                                                 with-connection!]])
  (:import (java.sql Timestamp)))

(defn find-menu-items! [db menu-id & menu-ids]
  (with-connection!
    db
    (fn [db]
      (let [menu-ids        (conj menu-ids menu-id)
            menu-items      (sql/query db
                                       (-> (h/select :*)
                                           (h/from :menu-item)
                                           (h/where [:in :menu-id menu-ids])
                                           (hsql/format)))
            menu-item-sizes (when (seq menu-items)
                              (->> (-> (h/select :*)
                                       (h/from :menu-item-size)
                                       (h/where [:in :menu-item-id (map :menu-item/id menu-items)])
                                       (hsql/format))
                                   (sql/query db)
                                   (map #(apply-db-str->keyword % :menu-item-size/amount-unit))
                                   (group-by :menu-item-size/menu-item-id)))]
        (->> menu-items
             (map #(assoc % :menu-item/sizes (get menu-item-sizes (:menu-item/id %) [])))
             (group-by :menu-item/menu-id))))))

(defn find-menu-item-by-id! [db menu-item-id]
  (with-connection!
    db
    (fn [db]
      (when-let [[item] (sql/find-by-keys db :menu-item {:id menu-item-id})]
        (assoc item :menu-item/sizes (sql/find-by-keys db :menu-item-size {:menu-item-id menu-item-id}))))))

(defn find-all-menus!
  ([db]
   (find-all-menus! db false false false))
  ([db include-inactive?]
   (find-all-menus! db include-inactive? false false))
  ([db include-inactive? detailed?]
   (find-all-menus! db include-inactive? detailed? false))
  ([db include-inactive? detailed? include-deleted?]
   (with-connection!
     db
     (fn [db]
       (let [menus      (->> (sql/find-by-keys
                              db
                              :menu
                              (if (and include-inactive? include-deleted?)
                                :all
                                (cond-> {}
                                  (not include-inactive?) (assoc :active true)
                                  (not include-deleted?) (assoc :deleted false))))
                             (map #(apply-db-str->keyword % :menu/duration-type)))
             menu-items (when (and detailed? (seq menus))
                          (->> menus
                               (map :menu/id)
                               (apply (partial find-menu-items! db))))]
         (mapv #(assoc % :menu/items (get menu-items (:menu/id %)))
               menus))))))

(defn insert-menu! [db menu]
  (sql/insert! db :menu
               (-> menu
                   (dissoc :menu/items)
                   (apply-keyword->db-str :menu/duration-type)
                   (update :menu/end-time #(when % (Timestamp/from (.toInstant %)))))))

(defn find-menu-by-id! [db menu-id]
  (with-connection!
    db
    (fn [db]
      (when-let [[menu] (sql/find-by-keys db :menu {:menu/id menu-id})]
        (-> menu
            (assoc :menu/items (get (find-menu-items! db menu-id) menu-id))
            (apply-db-str->keyword :menu/duration-type))))))

(defn update-menu! [db menu]
  (-> (sql/update! db :menu
                   (-> menu
                       (dissoc :menu/items)
                       (apply-keyword->db-str :menu/duration-type)
                       (update :menu/end-time #(when % (Timestamp/from (.toInstant %)))))
                   (select-keys menu [:menu/id]))
      ::jdbc/update-count
      (pos?)))

(defn delete-menu! [db menu-id]
  (-> (sql/update! db :menu {:deleted true} {:id menu-id})
      ::jdbc/update-count
      (pos?)))

(defn insert-menu-item! [db menu-item]
  (sql/insert! db :menu-item (-> menu-item
                                 (dissoc :menu-item/sizes))))

(defn update-menu-item! [db menu-item]
  (-> (sql/update! db :menu-item (-> menu-item
                                     (dissoc :menu-item/sizes))
                   (select-keys menu-item [:menu-item/id :menu-item/menu-id]))
      ::jdbc/update-count
      (pos?)))

(defn delete-menu-item! [db menu-item]
  (-> (sql/delete! db :menu-item menu-item)
      ::jdbc/update-count
      (pos?)))

(defn insert-menu-item-size! [db menu-item-size]
  (sql/insert! db :menu-item-size (apply-keyword->db-str menu-item-size :menu-item-size/amount-unit)))

(defn update-menu-item-size! [db menu-item-size]
  (-> (sql/update! db :menu-item-size
                   (apply-keyword->db-str menu-item-size :menu-item-size/amount-unit)
                   (select-keys menu-item-size [:menu-item-size/id :menu-item-size/menu-id :menu-item-size/menu-item-id]))
      ::jdbc/update-count
      (pos?)))

(defn delete-menu-item-size! [db menu-item-size]
  (-> (sql/delete! db :menu-item-size menu-item-size)
      ::jdbc/update-count
      (pos?)))

(defn find-active-menu-items! [db]
  (->> (-> (h/select :menu-item/*)
           (h/from :menu-item)
           (h/join :menu [:= :menu/id :menu-item/menu-id])
           (h/where [:= :menu/active true])
           (hsql/format))
       (sql/query db)))

(defn find-menu-item-sizes-by-items! [db & menu-item-ids]
  (->> (-> (h/select :menu-item-size/*)
           (h/from :menu-item-size)
           (h/where [:in :menu-item-id menu-item-ids])
           (hsql/format))
       (sql/query db)
       (map #(apply-db-str->keyword % :menu-item-size/amount-unit))))

(defn find-menu-item-sizes! [db & menu-item-size-ids]
  (when (seq menu-item-size-ids)
    (->> menu-item-size-ids
         (#(sql/query db (-> (h/select :menu-item-size/*)
                             (h/from :menu-item-size)
                             (h/where [:in :menu-item-size/id %])
                             (hsql/format))))
         (map #(apply-db-str->keyword % :menu-item-size/amount-unit)))))

(defn decrement-menu-item-size-available-quantity! [db menu-item-size-id qty]
  (-> (jdbc/execute-one! db
                         (hsql/format
                          (-> (h/update :menu-item-size)
                              (h/set {:available-quantity [:case
                                                           [:>= :available-quantity qty]
                                                           [:- :available-quantity qty]
                                                           :else :available-quantity]})
                              (h/where [:= :id menu-item-size-id]
                                       [:or [:< :available-quantity 0]
                                        [:>= :available-quantity qty]]))))
      ::jdbc/update-count
      pos?))

(defn increment-menu-item-size-available-quantity! [db menu-item-size-id qty]
  (jdbc/execute-one! db
                     (hsql/format
                      (-> (h/update :menu-item-size)
                          (h/set {:available-quantity [:case
                                                       [:>= :available-quantity 0]
                                                       [:+ :available-quantity qty]
                                                       :else :available-quantity]})
                          (h/where [:= :id menu-item-size-id])))))
