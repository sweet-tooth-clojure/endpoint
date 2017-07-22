(ns sweet-tooth.endpoint.datomic
  "Helper functions for working with datomic entities.

  Transaction results are put in `:result` in the context."
  (:require [sweet-tooth.endpoint.utils :as u]
            [medley.core :as medley]
            [datomic.api :as d]
            [integrant.core :as ig]))

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
      u/params
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

(defn ctx->update-map
  [ctx]
  (-> ctx
      u/params
      u/remove-nils-from-map
      (assoc :db/id (u/ctx-id ctx))
      (dissoc :id)))

(defn update
  [ctx]
  (d/transact (conn ctx) [(ctx->update-map ctx)]))

(defn delete
  [ctx]
  (d/transact (conn ctx) [[:db.fn/retractEntity (u/ctx-id ctx)]]))

(def db-after (u/get-ctx [:result :db-after]))
(def db-before (u/get-ctx [:result :db-before]))

(defn auth-owns?
  "Check if the user entity is the same as the authenticated user"
  [ctx db user-key]
  (let [ent (d/entity db (u/ctx-id ctx))]
    (= (:db/id (user-key ent)) (u/auth-id ctx))))

(defmethod ig/init-key :sweet-tooth.endpoint/datomic [_ config]
  (assoc config :conn (d/connect (:uri config))))
