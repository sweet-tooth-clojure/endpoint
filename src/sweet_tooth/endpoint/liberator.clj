(ns sweet-tooth.endpoint.liberator
  (:require [liberator.representation :as lr]
            ;; this loads support for transit into liberator
            [io.clojure.liberator-transit]
            [com.flyingmachine.liberator-unbound :as lu]
            [ring.util.response :as resp]
            [buddy.auth :as buddy]
            [medley.core :as medley]
            [compojure.core :refer [routes]]
            [flyingmachine.webutils.validation :refer [if-valid]]))

;; -------------------------
;; Working with liberator context
;; -------------------------
(defn get-ctx
  [path]
  (let [path (if (vector? path) path [path])]
    (fn [ctx]
      (get-in ctx path))))

;; Expect consumers to store records when e.g. fetching a single ent
;; by id in record
(def record (get-ctx :record))
(def errors (get-ctx :errors))
(def params (get-ctx [:request :params]))

(defn ctx-id
  "Get id from the params, try to convert to number
  TODO this is better solved with routing type hint"
  [ctx & [id-key]]
  (let [id-key (or id-key :id)]
    (if-let [id (id-key (params ctx))]
      (Long/parseLong id)
      (:db/id (params ctx)))))

(defn transit-response
  [payload & [opts]]
  (lr/ring-response
    payload
    (merge {:status (get opts :status 200)
            :headers {"media-type" "application/transit+json"}}
           opts)))

(defn errors-map
  "Add errors to context, setting media-type in case liberator doesn't
  get to that decision"
  [errors]
  {:errors errors
   :representation {:media-type "application/transit+json"}})

(defn error-response
  "For cases where the error happens before the request gets to liberator"
  [status errors]
  (transit-response {:errors errors} {:status status}))

(defn ->ctx
  "Make it easy to thread data into liberator context"
  [x k]
  {k x})

(defn exists-fn
  "Given a function to retrieve a record, store it under `:record` in the
  context if it exists"
  [ent-fn]
  (fn [ctx]
    (if-let [ent (ent-fn ctx)]
      {:record ent}
      false)))

(def authorization-error
  (errors-map {:authorization "Not authorized."}))
(def authentication-error
  (errors-map {:authentication "You must be logged in to do that."}))

;; Assumes buddy
(def auth (get-ctx [:request :identity]))

(defn authenticated?
  "To use with liberator's :authorized? decision"
  [ctx]
  (if (buddy/authenticated? (:request ctx))
    [true {:auth (:identity (:request ctx))}]
    [false authentication-error]))

(defn auth-with
  "If any auth function authenticates the context, return true. Used
  to e.g. auth by ownership or adminship"
  [& fns]
  (fn [ctx]
    (if (some #(% ctx) fns)
      true
      [false authorization-error])))

(defn auth-id
  "Retrieve the ID of authenticated user. Assumes `:auth-id-key` is in
  the ctx"
  [{:keys [auth-id-key] :as ctx}]
  {:pre [auth-id-key]}
  ((:auth-id-key ctx) (auth ctx)))

(defn assoc-user
  [ctx user-key]
  (assoc-in ctx
            [:request :params user-key]
            (auth-id ctx)))

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

;; Generating liberator resources without defresource
;; TODO check if there's something better than handle-malformed
(def decision-defaults
  "A 'base' set of liberator resource decisions for list, create,
  show, update, and delete"
  (let [errors-in-ctx (fn [ctx]
                        (merge (select-keys ctx [:errors])
                               {:representation {:media-type "application/transit+json"}}))
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
                          :handle-created record})
     :show base
     :update (merge base {:allowed-methods [:put]})
     :delete (merge base {:allowed-methods [:delete]
                          :respond-with-entity? false})}))

(def resource-route
  "A function that takes a path and decision spec and produces
  compojure endpoint"
  (lu/bundle {:collection [:list :create]
              :entry [:show :update :delete]}
             decision-defaults))

;; TODO move this somwhere else, it's not really liberator
(defn html-resource
  "Serve resource at `path` as html"
  [path]
  (-> (resp/resource-response path)
      (resp/content-type "text/html")))


(defn initialize-decisions
  [decisions context-initializer]
  (medley/map-vals
    (fn [decision]
      (assoc decision :initialize-context context-initializer))
    decisions))

(defn endpoint
  "Makes it easier to apply an initialize-context to every decision"
  [route decisions context-initializer]
  (fn [opts]
    (routes (resource-route route
                            (fn [opts]
                              (initialize-decisions (decisions opts)
                                                    (fn [ctx]
                                                      (context-initializer ctx opts))))
                            opts))))
