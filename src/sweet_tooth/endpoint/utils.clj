(ns sweet-tooth.endpoint.utils
  (:require [com.flyingmachine.liberator-unbound :as lu]
            [com.flyingmachine.datomic-junk :as dj]
            [datomic.api :as d]
            [flyingmachine.webutils.validation :refer [if-valid]]
            [buddy.auth :as buddy]
            [medley.core :as medley]
            ;; this loads support for transit into liberator
            [io.clojure.liberator-transit]
            [ring.util.response :as resp]
            [liberator.representation :as lr]))

;; util
(defn remove-nils-from-map
  [record]
  (into {} (remove (comp nil? second) record)))

;; Working with liberator context
(defn record-in-ctx
  [ctx]
  (:record ctx))

(defn errors-in-ctx
  ([]
   (errors-in-ctx {}))
  ([opts]
   (fn [ctx]
     (merge {:errors (:errors ctx)} opts))))

(defn params
  [ctx]
  (get-in ctx [:request :params]))

(defn merge-params
  [ctx p]
  (update-in ctx [:request :params] merge p))

(defn errors-map
  [errors]
  {:errors errors
   :representation {:media-type "application/transit+json"}})

(defn error-response
  [status errors]
  (lr/ring-response
    {:body {:errors errors}
     :status status
     :headers {"media-type" "application/transit+json"}}))

(def authorization-error (errors-map {:authorization "Not authorized."}))
(def authentication-error (errors-map {:authorization "You must be logged in to do that."}))

(defn auth
  [ctx]
  (get-in ctx [:request :identity]))

(defn auth-id
  [ctx]
  (:db/id (auth ctx)))

(defn authenticated?
  [ctx]
  (if (buddy/authenticated? (:request ctx))
    [true {:auth (:identity (:request ctx))}]
    [false authentication-error]))


(defn add-record
  "Return a map that gets added to the ctx"
  [r]
  (if r {:record r}))

(defn add-result
  [r]
  (if r {:result r}))

(defn ctx-id
  [ctx & [id-key]]
  (let [id-key (or id-key :id)]
    (if-let [id (id-key (params ctx))]
      (Long/parseLong id)
      (:db/id (params ctx)))))

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
  [ctx]
  (-> ctx
      params
      remove-nils-from-map
      assoc-tempid))

(defn conn
  [ctx]
  (get-in ctx [:db :conn]))

(defn create
  [ctx]
  (d/transact (conn ctx) [(ctx->create-map ctx)]))

(defn ctx->update-map
  [ctx]
  (-> ctx
      params
      remove-nils-from-map
      (assoc :db/id (ctx-id ctx))
      (dissoc :id)))

(defn update
  [ctx]
  (d/transact (conn ctx) [(ctx->update-map ctx)]))

(defn delete
  [ctx]
  (d/transact (conn ctx) [[:db.fn/retractEntity (ctx-id ctx)]]))

(defn assoc-user
  [ctx user-key]
  (assoc-in ctx
            [:request :params user-key]
            (auth-id ctx)))

(defn db-after
  [ctx]
  (get-in ctx [:result :db-after]))

(defn db-before
  [ctx]
  (get-in ctx [:result :db-before]))

;; Generating liberator resources without defresource
(def decision-defaults
  ^{:doc "A 'base' set of liberator resource decisions for list,
    create, show, update, and delete"}
  (let [errors-in-ctx (errors-in-ctx {:representation {:media-type "application/transit+json"}})
        base {:available-media-types ["application/transit+json"
                                      "application/transit+msgpack"
                                      "application/json"]
              :allowed-methods [:get]
              :authorized? true
              :handle-unauthorized errors-in-ctx
              :handle-malformed errors-in-ctx
              :respond-with-entity? true
              :new? false}]
    {:list base
     :create (merge base {:allowed-methods [:post]
                          :new? true
                          :handle-created record-in-ctx})
     :show base
     :update (merge base {:allowed-methods [:put]})
     :delete (merge base {:allowed-methods [:delete]
                          :respond-with-entity? false})}))

(def resource-route
  ^{:doc "A function that takes a path and decision spec 
    and produces compojure endpoint"}
  (lu/bundle {:collection [:list :create]
              :entry [:show :update :delete]}
             decision-defaults))

(defn html-resource [path]
  (-> (resp/resource-response path)
      (resp/content-type "text/html")))

(defmacro validator
  "Used in invalid? which is why truth values are reversed"
  ([validation]
   `(validator ~(gensym) ~validation))
  ([ctx-sym validation]
   `(fn [~ctx-sym]
      (if-valid
       (params ~ctx-sym) ~validation errors#
       false
       [true (errors-map errors#)]))))
