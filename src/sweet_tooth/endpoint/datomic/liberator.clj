(ns sweet-tooth.endpoint.datomic.liberator
  "Helper functions for working with datomic entities.

  Transaction results are put in `:result` in the liberator context."
  (:require [sweet-tooth.endpoint.utils :as u]
            [sweet-tooth.endpoint.liberator :as el]
            [medley.core :as medley]
            [datomic.api :as d]
            [integrant.core :as ig]
            [com.flyingmachine.datomic-junk :as dj]))

(defn ctx-id
  "Get id from the params, try to convert to number
  TODO this is better solved with routing type hint"
  [ctx & [id-key]]
  (let [id-key (or id-key :id)]
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

(defn created-entity
  "Use when you've created a single entity and stored the tx result under :result"
  [{:keys [result]}]
  (d/entity (:db-after result) (first (vals (:tempids result)))))

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

(defn delete
  [ctx]
  (d/transact (conn ctx) [[:db.fn/retractEntity (ctx-id ctx)]]))

(def db-after (el/get-ctx [:result :db-after]))
(def db-before (el/get-ctx [:result :db-before]))

(defn auth-owns?
  "Check if the user entity is the same as the authenticated user"
  [ctx db user-key]
  (let [ent (d/entity db (ctx-id ctx))]
    (= (:db/id (user-key ent)) (el/auth-id ctx))))
