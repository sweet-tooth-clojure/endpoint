(ns sweet-tooth.endpoint.datomic.session-store
  (:require [datomic.api :as d]
            [com.flyingmachine.datomic-junk :as dj]
            [integrant.core :as ig]
            [ring.middleware.session.store :as ss]))

;;--------
;; add a datomic session store for ring's session middleware
;;--------
(defn str->uuid [s]
  (when s
    (try (java.util.UUID/fromString s)
         (catch java.lang.IllegalArgumentException _e nil))))

(deftype DatomicSessionStore [key-attr data-attr partition auto-key-change? db]
  ss/SessionStore
  (ss/read-session [_ key]
    (let [key (and key (str->uuid key))]
      (if-let [data (and key (data-attr (dj/one (d/db (:conn db)) [key-attr key])))]
        (read-string data)
        {})))
  (ss/write-session [_ key data]
    (let [uuid-key    (str->uuid key)
          sess-data   (str data)
          eid         (when uuid-key (:db/id (dj/one (d/db (:conn db)) [key-attr uuid-key])))
          key-change? (or (not eid) auto-key-change?)
          uuid-key    (if key-change? (java.util.UUID/randomUUID) uuid-key)
          txdata      {:db/id    (or eid (d/tempid partition))
                       key-attr  uuid-key
                       data-attr sess-data}]
      @(d/transact (:conn db) [txdata])
      (str uuid-key)))
  (ss/delete-session [_ key]
    (when-let [session-id (ffirst (d/q [:find '?c :where ['?c key-attr (str->uuid key)]]
                                       (d/db (:conn db))))]
      @(d/transact (:conn db) [[:db.fn/retractEntity session-id]]))
    nil))

(defn datomic-session-store
  [{:keys [key-attr data-attr partition auto-key-change? db]
    :or   {key-attr         :user-session/key
           data-attr        :user-session/data
           partition        :db.part/user
           auto-key-change? true}}]
  (DatomicSessionStore. key-attr data-attr partition auto-key-change? db))

(defmethod ig/init-key :sweet-tooth.endpoint.datomic/session-store [_ config]
  (datomic-session-store config))
