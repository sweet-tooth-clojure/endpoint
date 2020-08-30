(ns sweet-tooth.endpoint.datomic.liberator
  "Helper functions for working with datomic entities.

  Transaction results are put in `:result` in the liberator context."
  (:refer-clojure :exclude [update])
  (:require [datomic.api :as d]
            [medley.core :as medley]
            [sweet-tooth.endpoint.liberator :as el]))

(def db-after (el/get-ctx [:result :db-after]))
(def db-before (el/get-ctx [:result :db-before]))

(defn assoc-tempid
  [x]
  (assoc x :db/id (d/tempid :db.part/user)))

(defn conn
  [ctx]
  (get-in ctx [:db :conn]))

(defn db
  [ctx]
  (d/db (conn ctx)))

(defn auth-owns?
  "Check if the user entity is the same as the authenticated user"
  [ctx db user-key & [id-key]]
  (let [ent (d/entity db (el/ctx-id ctx id-key))]
    (= (:db/id (user-key ent)) (el/auth-id ctx))))

;;-----
;; get
;;-----
(defn pull-ctx-id
  [ctx]
  (let [e (d/pull (db ctx) '[:*] (el/ctx-id ctx))]
    (when (not= [:db/id] (keys e))
      e)))

;;-----
;; create
;;-----

(defn ctx->create-map
  "given a liberator context, returns a map that's ready to be used in a
  datomic transaction to create an entity"
  [ctx]
  (->> ctx
       el/params
       (medley/filter-vals some?)
       assoc-tempid))

(defn create
  "transact the params in a context"
  [ctx]
  (d/transact (conn ctx) [(ctx->create-map ctx)]))

(defn created-id
  "return id of created entity, assuming you've only created one entity"
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

;;-----
;; update
;;-----

(defn ctx->update-map
  "cleans up params from liberator context"
  [ctx & [id-key]]
  (-> ctx
      el/params
      (->> (medley/filter-vals some?))
      (assoc (el/req-id-key ctx id-key) (el/ctx-id ctx id-key))))

(defn update
  [ctx]
  (d/transact (conn ctx) [(ctx->update-map ctx)]))

(defn updated-entity
  "Use when you've updated a single entity and stored the tx result
  under :result"
  [ctx]
  (d/entity (db-after ctx) (el/ctx-id ctx)))

(defn updated-pull
  "Differs from `created-entity` in that it returns a map, not a
  map-like Datomic Entity"
  [ctx & [attrs]]
  (let [id-key (el/req-id-key ctx)
        id-val (el/ctx-id ctx)]
    (d/q {:find [(list 'pull '?e (or attrs '[:*]))]
          :in   '[$ ?id-attr ?id-val]
          :where '[[?e ?id-attr ?id-val]]}
         (db-after ctx) id-key id-val)))

;;-----
;; delete
;;-----

(defn delete
  [ctx & [id-key]]
  (d/transact (conn ctx) [[:db.fn/retractEntity (el/ctx-id ctx id-key)]]))

;;-----
;; compose tx functions, put result in :result
;;-----

(defn deref->:result
  "deref a transaction and put the result in `:result` of ctx."
  [tx]
  ((comp #(el/->ctx % :result) deref) tx))

(defn transact->ctx
  [tx-fn]
  (fn [ctx]
    (merge ctx
           (-> ctx tx-fn deref->:result))))

(def update->:result (transact->ctx update))
(def delete->:result (transact->ctx delete))
(def create->:result (transact->ctx create))
