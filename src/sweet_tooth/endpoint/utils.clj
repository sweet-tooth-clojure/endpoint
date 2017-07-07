(ns sweet-tooth.endpoint.utils
  (:require [com.flyingmachine.liberator-unbound :as lu]
            [flyingmachine.webutils.validation :refer [if-valid]]
            [buddy.auth :as buddy]
            ;; this loads support for transit into liberator
            [io.clojure.liberator-transit]
            [ring.util.response :as resp]
            [liberator.representation :as lr]))

;; util
(defn update-vals
  "Takes a map to be updated, x, and a map of
  {[k1 k2 k3] update-fn-1
   [k4 k5 k6] update-fn-2}
  such that such that k1, k2, k3 are updated using update-fn-1
  and k4, k5, k6 are updated using update-fn-2"
  [x update-map]
  (reduce (fn [x [keys update-fn]]
            (reduce (fn [x k] (update x k update-fn))
                    x
                    keys))
          x
          update-map))

(defn remove-nils-from-map
  [record]
  (into {} (remove (comp nil? second) record)))

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

(defn errors-map
  "Add errors to context, setting media-type in case liberator doesn't
  get to that decision"
  [errors]
  {:errors errors
   :representation {:media-type "application/transit+json"}})

(defn error-response
  "For cases where the error happens before the request gets to liberator"
  [status errors]
  (lr/ring-response
    {:body {:errors errors}
     :status status
     :headers {"media-type" "application/transit+json"}}))

(defn ->ctx
  "Make it easy to thread data into liberator context"
  [x k]
  {k x})

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
;; Organize response records for easy frontend consumption
;; -------------------------
(defn key-by
  [k xs]
  (into {} (map (juxt k identity) xs)))

(defn format
  "Expects `e`, be it map or seq, to have ent-type defined in metadata"
  [e id-key]
  (let [{:keys [ent-type]} (meta e)]
    {ent-type (key-by id-key (if (map? e) [e] e))}))

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
                        (merge (errors ctx)
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

(defn html-resource
  "Serve resource at `path` as html"
  [path]
  (-> (resp/resource-response path)
      (resp/content-type "text/html")))
