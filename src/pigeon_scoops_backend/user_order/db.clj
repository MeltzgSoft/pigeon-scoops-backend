(ns pigeon-scoops-backend.user-order.db
  (:require [honey.sql :as hsql]
            [honey.sql.helpers :as h]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [pigeon-scoops-backend.utils :refer [apply-db-str->keyword
                                                 apply-keyword->db-str
                                                 keyword->db-str
                                                 with-connection!]]
            [pigeon-scoops-backend.units.common :refer [reduce-amounts]]
            [pigeon-scoops-backend.menu.db :as menu-db]
            [pigeon-scoops-backend.user-order.transforms :refer [infer-order-status]]))

(defn assoc-item-size-quantity! [db & order-items]
  (let [menu-item-sizes (->> order-items
                             (map :order-item/menu-item-size-id)
                             (set)
                             (apply (partial menu-db/find-menu-item-sizes! db))
                             (map #(vector (:menu-item-size/id %) %))
                             (into {}))]
    (map #(let [menu-item-size (get menu-item-sizes (:order-item/menu-item-size-id %))]
            (if menu-item-size
              (assoc % :order-item/menu-item-size-quantity
                     (first
                      (reduce-amounts /
                                      (:order-item/amount %)
                                      (:order-item/amount-unit %)
                                      (:menu-item-size/amount menu-item-size)
                                      (:menu-item-size/amount-unit menu-item-size))))
              %))
         order-items)))

(defn find-all-order-items! [db order-id & order-ids]
  (with-connection!
    db (fn [db]
         (->> (conj order-ids order-id)
              (#(sql/query db (-> (h/select :order-item/*)
                                  (h/from :order-item)
                                  (h/where [:in :order-item/order-id %])
                                  (hsql/format))))
              (map #(apply-db-str->keyword % :order-item/status :order-item/amount-unit))
              (apply (partial assoc-item-size-quantity! db))
              (group-by :order-item/order-id)))))

(defn find-all-items-by-status! [db status]
  (with-connection!
    db (fn [db]
         (->> (sql/query db (-> (h/select :order-item/*)
                                (h/from :order-item)
                                (h/where [:= :order-item/status (keyword->db-str status)])
                                (hsql/format)))
              (map #(apply-db-str->keyword % :order-item/status :order-item/amount-unit))
              (apply (partial assoc-item-size-quantity! db))))))

(defn find-all-orders!
  [db user-id detailed?]
  (with-connection!
    db (fn [db]
         (let [orders (sql/find-by-keys
                       db
                       :user-order
                       (cond-> {:user-order/deleted false}
                         (not= user-id :admin) (assoc :user-order/user-id user-id)))
               order-items (when (seq orders)
                             (->> orders
                                  (map :user-order/id)
                                  (apply (partial find-all-order-items! db))))]
           (->> orders
                (mapv #(assoc % :user-order/status (infer-order-status (get order-items (:user-order/id %)))
                              :user-order/items (when detailed?
                                                  (get order-items (:user-order/id %)))))
                (mapv #(apply-db-str->keyword % :user-order/amount-unit :user-order/status)))))))

(defn find-order-by-id! [db order-id]
  (with-connection!
    db (fn [db]
         (let [[order] (sql/find-by-keys db :user-order {:id order-id})
               items (get (find-all-order-items! db order-id)
                          order-id)]
           (when (seq order)
             (-> order
                 (assoc :user-order/status (infer-order-status items)
                        :user-order/items items)
                 (apply-db-str->keyword :user-order/amount-unit :user-order/status)))))))

(defn find-order-item-by-id! [db order-item-id]
  (with-connection!
    db (fn [db]
         (->> (sql/find-by-keys db :order-item {:id order-item-id})
              (map #(apply-db-str->keyword % :order-item/status :order-item/amount-unit))
              (apply (partial assoc-item-size-quantity! db))
              (first)))))

(defn insert-order! [db order]
  (sql/insert! db :user-order (-> order
                                  (dissoc :user-order/items)
                                  (apply-keyword->db-str :user-order/amount-unit))))

(defn update-order! [db order]
  (-> (sql/update! db :user-order (-> order
                                      (dissoc :user-order/items)
                                      (apply-keyword->db-str :user-order/amount-unit))
                   (select-keys order [:user-order/id]))
      ::jdbc/update-count
      (pos?)))

(defn delete-order! [db order-id]
  (-> (sql/update! db :user-order {:deleted true} {:user-order/id order-id})
      ::jdbc/update-count
      (pos?)))

(defn insert-order-item! [db item]
  (sql/insert! db :order-item (apply-keyword->db-str item :order-item/status :order-item/amount-unit)))

(defn update-order-item! [db item]
  (-> item
      (apply-keyword->db-str :order-item/status :order-item/amount-unit)
      (#(sql/update! db :order-item %
                     (select-keys % [:order-item/id])))
      ::jdbc/update-count
      (pos?)))

(defn delete-order-item! [db item]
  (-> (sql/delete! db :order-item item)
      ::jdbc/update-count
      (pos?)))

(defn bulk-status-update! [db status-update-map & recipe-ids]
  (jdbc/with-transaction
    [db db]
    (mapv (fn [[from to]]
            (sql/query db (-> (h/update :order-item)
                              (h/set {:order-item/status (keyword->db-str to)})
                              (h/where [:in :order-item/recipe-id recipe-ids]
                                       [:= :order-item/status (keyword->db-str from)])
                              (hsql/format))))
          status-update-map)))
