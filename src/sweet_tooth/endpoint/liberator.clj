(ns sweet-tooth.endpoint.liberator
  "Includes:

  * Utility functions for retrieving common values from the
  context (`record`, `errrors`, `params`)
  * A template of decision defaults appropriate for an SPA
  * Utility functions for auth

  Introduces loose convention of putting a record under `:record` in
  the context

  Extends liberator's representations to handle transit"
  (:require [buddy.auth :as buddy]
            [flyingmachine.webutils.validation :refer [if-valid]]
            [liberator.representation :as lr]
            [medley.core :as medley]
            [ring.util.response :as resp]
            [sweet-tooth.describe :as d]
            [sweet-tooth.endpoint.format :as ef]
            [clojure.string :as str]))

;; -------------------------
;; returning transit
;; -------------------------

(defrecord TransitResponse [data]
  liberator.representation.Representation
  (as-response [_ context]
    {:body    data
     :headers {"Content-Type" (-> (get-in context [:representation :media-type])
                                  (str/replace #"st-segments" "transit"))}}))

(defmethod lr/render-map-generic "application/transit+json"
  [data _ctx]
  (->TransitResponse data))

(defmethod lr/render-map-generic "application/transit+msgpack"
  [data _ctx]
  (->TransitResponse data))

(defmethod lr/render-seq-generic "application/transit+json"
  [data _ctx]
  (->TransitResponse data))

(defmethod lr/render-seq-generic "application/transit+msgpack"
  [data _ctx]
  (->TransitResponse data))

(defn transit-response
  [payload & [opts]]
  (lr/ring-response
   payload
   (merge {:status (get opts :status 200)
           :headers {"media-type" "application/transit+json"}}
          opts)))

(defn segment-response
  [data ctx]
  (->TransitResponse (ef/format-body-data data ctx)))

(defmethod lr/render-map-generic "application/st-segments+json"
  [data ctx]
  (segment-response data ctx))

(defmethod lr/render-map-generic "application/st-segments+msgpack"
  [data ctx]
  (segment-response data ctx))

(defmethod lr/render-seq-generic "application/st-segments+json"
  [data ctx]
  (segment-response data ctx))

(defmethod lr/render-seq-generic "application/st-segments+msgpack"
  [data ctx]
  (segment-response data ctx))


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

(defn errors-map
  "Add errors to context, setting media-type in case liberator doesn't
  get to that decision"
  [errors]
  {:errors         errors
   :representation {:media-type "application/st-segments+json"}})

(defn error-response
  "For cases where the error happens before the request gets to liberator"
  [status errors]
  (transit-response [[:errors errors]] {:status status}))

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

(defn req-id-key
  [ctx & [id-key]]
  (or id-key (:id-key ctx) :id))

(defn ctx-id
  "Get id from the params"
  [ctx & [id-key]]
  (get (params ctx) (req-id-key ctx id-key)))

(defn update-params
  [ctx f & args]
  (apply update-in ctx [:request :params] f args))

(defn assoc-in-params
  [ctx path v]
  (assoc-in ctx (into [:request :params] path) v))

(defn assoc-param
  [ctx k v]
  (assoc-in ctx [:request :params k] v))

(defn assoc-params
  [ctx params]
  (assoc-in ctx [:request :params] params))

;; -------------------------
;; decisions
;; -------------------------

;; Generating liberator resources without defresource
;; TODO check if there's something better than handle-malformed
(def decision-defaults
  "A base set of liberator resource decisions"
  (let [errors-in-ctx (fn [ctx] [:errors (:errors ctx)])
        base          {:available-media-types ["application/st-segments+json"
                                               "application/st-segments+msgpack"
                                               "application/transit+json"
                                               "application/transit+msgpack"
                                               "application/json"]
                       :allowed-methods       [:get]
                       :authorized?           true
                       :handle-unauthorized   errors-in-ctx
                       :handle-malformed      errors-in-ctx
                       :respond-with-entity?  true
                       :new?                  false}]
    {:get    base
     :post   (merge base {:allowed-methods [:post]
                          :new?            true
                          :handle-created  record})
     :put    (merge base {:allowed-methods [:put]})
     :patch  (merge base {:allowed-methods [:patch]})
     :head   (merge base {:allowed-methods [:head]})
     :delete (merge base {:allowed-methods      [:delete]
                          :respond-with-entity? false})}))

(defn initialize-decisions
  "Adds `:initalize` to multiple decisions. Used by
  `sweet-tooth.endpoint.module.liberator-reitit-router` to inject
  context values set in routes."
  [decisions context-initializer]
  (medley/map-vals
   (fn [decision]
     (assoc decision :initialize-context (if (fn? context-initializer)
                                           context-initializer
                                           (constantly context-initializer))))
   decisions))

;; -------------------------
;; validation
;; -------------------------

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

(defn validate-describe
  "Use describe lib to validate a request. Returns a function that's
  meant to be used with the `:malformed?` liberator decision"
  [rules & [describe-context]]
  (fn [ctx]
    (when-let [descriptions (d/describe (params ctx)
                                        rules
                                        (when describe-context (describe-context ctx)))]
      [true (errors-map (d/map-rollup-descriptions descriptions))])))

;; -------------------------
;; auth
;; -------------------------

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

;; -------------------------
;; misc
;; -------------------------

;; TODO move this somewhere else, it's not really liberator
(defn html-resource
  "Serve resource at `path` as html"
  [path]
  (-> (resp/resource-response path)
      (resp/content-type "text/html")))

(defn raw
  [response]
  (with-meta response {::ef/formatter ::ef/raw}))
