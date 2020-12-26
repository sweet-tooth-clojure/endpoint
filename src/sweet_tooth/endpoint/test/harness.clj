(ns sweet-tooth.endpoint.test.harness
  "Includes:

  * Macros and a fixture for dealing with a system for the duration of a test
  * Helpers for composing and dispatching requests
  * `read-body` multimethod for parsing response bodies of different types (transit, json etc)
  * assertions that work with response segments"
  (:require [cheshire.core :as json]
            [clojure.test :as test]
            [cognitect.transit :as transit]
            [com.rpl.specter :as specter]
            [integrant.core :as ig]
            [ring.mock.request :as mock]
            [reitit.core :as rc]
            [sweet-tooth.endpoint.system :as es]
            [clojure.pprint :as pprint]))

(def ^:dynamic *system* nil)

;; -------------------------
;; system wrapper helpers
;; -------------------------

(defn system
  "more assertive system retrieval"
  []
  (when-not *system*
    (throw (ex-info "sweet-tooth.endpoint.test.harness/*system* is nil but should be a system. Try adding (use-fixtures :each (system-fixture :test-system-name)) to your test namespace." {})))
  *system*)

(defmacro with-system
  "Bind dynamic system var to a test system."
  [config-name & body]
  `(binding [*system* (es/system ~config-name)]
     (let [return# (do ~@body)]
       (ig/halt! *system*)
       return#)))

(defmacro with-custom-system
  "Bind dynamic system var to a test system with a custom config."
  [config-name custom-config & body]
  `(binding [*system* (es/system ~config-name ~custom-config)]
     (let [return# (do ~@body)]
       (ig/halt! *system*)
       return#)))

(defn system-fixture
  "To be used with `use-fixtures`"
  [config-name]
  (fn [f]
    (with-system config-name
      (f))))

(defn component
  "Look up component in current test system"
  [component-key]
  (or (component-key (system))
      (throw (ex-info "Could not find component" {:component-key component-key}))))


;; ---
;; paths
;; ---

(defn router
  "The endpoint router."
  []
  (if-let [router-key (-> :st/router descendants first)]
    (router-key (system))
    (throw (ex-info "No router for *system*. Router should descend from :st/router. Default is :sweet-tooth.endpoint.module.liberator-reitit-router/reitit-router" {}))))

(defn path
  ([route-name]
   (path route-name {}))
  ([route-name route-params]
   (or (:path (rc/match-by-name (router) route-name route-params))
       (throw (ex-info "could not generate router path" {:route-name   route-name
                                                         :route-params route-params})))))

;; -------------------------
;; compose and dispatch requests
;; -------------------------

(defn handler
  "The root handler for the system. Used to perform requests."
  []
  (or (:duct.handler/root (system))
      (throw (ex-info "No request handler for *system*." {}))))

(defn transit-in
  "An input stream of json-encoded transit. For request bodies."
  [data]
  (let [out (java.io.ByteArrayOutputStream. 4096)]
    (transit/write (transit/writer out :json) data)
    (java.io.ByteArrayInputStream. (.toByteArray out))))

(defn headers
  "Add all headers"
  [req headers]
  (reduce-kv mock/header req headers))

(defmulti base-request*
  (fn [_method _url _params content-type]
    content-type))

(defmethod base-request* :transit-json
  [method url params _]
  (-> (mock/request method url)
      (mock/header :content-type "application/transit+json")
      (mock/header :accept "application/transit+json")
      (assoc :body (transit-in params))))

(defmethod base-request* :json
  [method url params _]
  (-> (mock/request method url)
      (mock/header :content-type "application/json")
      (mock/header :accept "application/json")
      (assoc :body (json/encode params))))

(defmethod base-request* :html
  [method url params _]
  (mock/request method url params))

(defmethod base-request* :default
  [method url params _]
  (base-request* method url params :transit-json))

(defn urlize
  [url & [params]]
  (if (keyword? url) (path url params) url))

(defn base-request**
  [method url params content-type]
  (base-request* method (urlize url params) params content-type))

(defn base-request
  ([method url]
   (base-request** method url {} nil))
  ([method url params-or-content-type]
   (if (keyword? params-or-content-type)
     (base-request** method url {} params-or-content-type)
     (base-request** method url params-or-content-type nil)))
  ([method url params content-type]
   (base-request** method url params content-type)))

(defn req
  "Perform a request with the system's root handler"
  [& args]
  ((handler) (apply base-request args)))

;; -------------------------
;; read responses
;; -------------------------

(defmulti read-body "Read body according to content type"
  (fn [{:keys [headers]}]
    (->> (or (get headers "Content-Type")
             (get headers "content-type"))
         (re-matches #"(.*?)(;.*)?")
         second)))

(defmethod read-body "application/transit+json"
  [{:keys [body]}]
  (-> body
      (transit/reader :json)
      transit/read))

(defmethod read-body "application/json"
  [{:keys [body]}]
  (if (string? body)
    (json/parse-string body keyword)
    (json/parse-stream body keyword)))

(defmethod read-body :default
  [{:keys [body]}]
  (if (string? body)
    body
    (slurp body)))

;; -------------------------
;; segment assertions
;; -------------------------

(defn entity-segment? [segment]
  (= (first segment) :entity))

(defn response-entities
  "Walk response data and return all entities from entity segments"
  ([resp-data]
   (response-entities nil resp-data))
  ([ent-type resp-data]
   (->> resp-data
        (filter entity-segment?)
        (specter/select [specter/ALL 1 (or ent-type specter/MAP-VALS) specter/MAP-VALS]))))

(defn prep-comparison
  "When testing whether a response contains `test-ent-attrs`, we modify
  a response entity by:

  1. selecting only the keys that are present in
  `test-ent-attrs`. This allows us to do an `=` comparison that won't
  fail if the response entity contains attributes we don't want to
  test.

  2. Putting the result in a map to handle case where `resp-entity` is
  a map."
  [resp-entity test-ent-attrs]
  (into {} (select-keys resp-entity (keys test-ent-attrs))))

(defn comparison-entities
  "Returns a set that can be used to test if `test-ent-attrs` is
  contained in a response"
  ([test-ent-attrs resp-data]
   (comparison-entities test-ent-attrs resp-data))
  ([test-ent-attrs ent-type resp-data]
   (->> resp-data
        (response-entities ent-type)
        (map #(prep-comparison % test-ent-attrs))
        (set))))

(defn contains-entity?
  "Request's response data creates entity of type `ent-type` that has
  key/value pairs identical to `test-ent-attrs`."
  ([resp-data test-ent-attrs]
   (contains-entity? resp-data nil test-ent-attrs))
  ([resp-data ent-type test-ent-attrs]
   ((comparison-entities test-ent-attrs ent-type resp-data)
    test-ent-attrs)))

(defmacro assert-response-contains-one-entity-like
  "Request's response contains only one entity, and that entity is like
  `test-ent-attrs`. Advantage of using this over
  `assert-response-contains-entity-like` is it uses `(is (= ...))`, so
  in test reports you get the diff between expected and actual."
  ([resp-data test-ent-attrs]
   `(assert-response-contains-one-entity-like ~resp-data nil ~test-ent-attrs))
  ([resp-data ent-type test-ent-attrs]
   `(let [test-ent-attrs#      (into {} ~test-ent-attrs)
          [ent# :as entities#] (response-entities ~ent-type ~resp-data)
          c#                   (count entities#)]
      (test/is (= 1 c#)
               (str "Response should contain 1 entity. It had "
                    c#
                    ". Consider using `assert-response-contains-entity-like`. "))
      (test/is (= (prep-comparison ent# test-ent-attrs#)
                  test-ent-attrs#)
               (str "First response entity:\n"
                    (with-out-str (pprint/pprint ent#)))))))

(defmacro assert-response-contains-entity-like
  "Request's response data creates entity of type `ent-type` (optional)
  that has key/value pairs identical to `test-ent-attrs`"
  ([resp-data test-ent-attrs]
   `(assert-response-contains-entity-like ~resp-data nil ~test-ent-attrs))
  ([resp-data ent-type test-ent-attrs]
   `(let [test-ent-attrs# (into {} ~test-ent-attrs)
          entities#       (comparison-entities test-ent-attrs# ~ent-type ~resp-data)]
      (test/is (contains? entities# test-ent-attrs#)))))
