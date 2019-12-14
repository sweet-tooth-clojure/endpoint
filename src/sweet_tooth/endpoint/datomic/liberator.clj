(ns sweet-tooth.endpoint.datomic.liberator
  "Helper functions for working with datomic entities.

  Transaction results are put in `:result` in the liberator context."
  (:require [sweet-tooth.endpoint.utils :as u]
            [sweet-tooth.endpoint.liberator :as el]
            [medley.core :as medley]
            [datomic.api :as d]
            [integrant.core :as ig]
            [com.flyingmachine.datomic-junk :as dj]))

(def db-after (el/get-ctx [:result :db-after]))
(def db-before (el/get-ctx [:result :db-before]))

(defn ctx-id
  "Get id from the params, try to convert to number
  TODO this is better solved with routing type hint"
  [ctx & [id-key]]
  (let [id-key (or id-key (:id-key ctx) :id)]
    (if-let [id (id-key (el/params ctx))]
      (Long/parseLong id)
      (:db/id (el/params ctx)))))

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

(defn created-id
  [{:keys [result]}]
  (first (vals (:tempids result))))

(defn created-entity
  "Use when you've created a single entity and stored the tx result
  under :result"
  [ctx]
  (d/entity (db-after ctx) (created-id ctx)))

(defn created-pull
  "Differs from `created-entity` in that it returns a map, not a
  map-like Datomic Entity"
  [ctx]
  (d/pull (db-after ctx) '[:*] (created-id ctx)))

(defn ctx->update-map
  [ctx]
  (-> ctx
      el/params
      u/remove-nils-from-map
      (assoc :db/id (ctx-id ctx))
      (dissoc :id)))

(defn update
  [ctx]
  (d/transact (conn ctx) [(ctx->update-map ctx)]))

(defn updated-entity
  "Use when you've updated a single entity and stored the tx result
  under :result"
  [ctx]
  (d/entity (db-after ctx) (ctx-id ctx)))

(defn created-pull
  "Differs from `created-entity` in that it returns a map, not a
  map-like Datomic Entity"
  [ctx]
  (d/pull (db-after ctx) '[:*] (ctx-id ctx)))

(defn delete
  [ctx]
  (d/transact (conn ctx) [[:db.fn/retractEntity (ctx-id ctx)]]))

(defn deref->:result
  "deref a transaction put the result in :result of ctx"
  [tx]
  ((comp #(el/->ctx % :result) deref) tx))

(def update->:result (comp deref->:result update))
(def delete->:result (comp deref->:result delete))
(def create->:result (comp deref->:result create))

(defn auth-owns?
  "Check if the user entity is the same as the authenticated user"
  [ctx db user-key]
  (let [ent (d/entity db (ctx-id ctx))]
    (= (:db/id (user-key ent)) (el/auth-id ctx))))
