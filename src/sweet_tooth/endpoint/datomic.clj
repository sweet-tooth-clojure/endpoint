(ns sweet-tooth.endpoint.datomic
  "Helper functions for working with datomic entities.

  Transaction results are put in `:result` in the liberator context."
  (:require [sweet-tooth.endpoint.utils :as u]
            [sweet-tooth.endpoint.liberator :as el]
            [medley.core :as medley]
            [datomic.api :as d]
            [integrant.core :as ig]
            [com.flyingmachine.datomic-junk :as dj])
  (:use [ring.middleware.session.store]))

(defn created-id
  [ctx]
  (-> ctx
      (get-in [:result :tempids])
      first
      second))

(defn assoc-tempid
  [x]
  (assoc x :db/id (d/tempid :db.part/user)))

(defn ctx->create-map
  ""
  [ctx]
  (-> ctx
      el/params
      u/remove-nils-from-map
      assoc-tempid))

(defn conn
  [ctx]
  (get-in ctx [:db :conn]))

(defn db
  [ctx]
  (d/db (conn ctx)))

(defn create
  [ctx]
  (d/transact (conn ctx) [(ctx->create-map ctx)]))

(defn created-entity
  "Use when you've created a single entity and stored the tx result under :result"
  [{:keys [result]}]
  (d/entity (:db-after result) (first (vals (:tempids result)))))

(defn ctx->update-map
  [ctx]
  (-> ctx
      el/params
      u/remove-nils-from-map
      (assoc :db/id (el/ctx-id ctx))
      (dissoc :id)))

(defn update
  [ctx]
  (d/transact (conn ctx) [(ctx->update-map ctx)]))

(defn delete
  [ctx]
  (d/transact (conn ctx) [[:db.fn/retractEntity (el/ctx-id ctx)]]))

(def db-after (el/get-ctx [:result :db-after]))
(def db-before (el/get-ctx [:result :db-before]))

(defn auth-owns?
  "Check if the user entity is the same as the authenticated user"
  [ctx db user-key]
  (let [ent (d/entity db (el/ctx-id ctx))]
    (= (:db/id (user-key ent)) (el/auth-id ctx))))

;; Provide datomic connection as a component to other components
(defmethod ig/init-key :sweet-tooth.endpoint/datomic [_ config]
  (assoc config :conn (d/connect (:uri config))))

;; add a datomic session store for ring's session middleware
(defn str->uuid [s]
  (when s
    (try (java.util.UUID/fromString s)
         (catch java.lang.IllegalArgumentException e nil))))

(deftype DatomicSessionStore [key-attr data-attr partition auto-key-change? db]
  SessionStore
  (read-session [_ key]
    (if key
      (if-let [data (data-attr (dj/one (d/db (:conn db)) [key-attr (str->uuid key)]))]
        (read-string data)
        {})
      {}))
  (write-session [_ key data]
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
  (delete-session [_ key]
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

(defmethod ig/init-key :sweet-tooth.endpoint/datomic-session-store [_ config]
  (datomic-session-store config))
