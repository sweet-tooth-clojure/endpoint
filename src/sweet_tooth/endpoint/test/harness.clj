(ns sweet-tooth.endpoint.test.harness
  (:require [cheshire.core :as json]
            [cognitect.transit :as transit]
            [integrant.core :as ig]
            [ring.mock.request :as mock]
            [sweet-tooth.endpoint.system :as es]))

(def ^:dynamic *system* nil)

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
  [config-name]
  (fn [f]
    (with-system config-name
      (f))))

(defn handler
  []
  (or (:duct.handler/root *system*)
      (throw (ex-info "No request handler for *system*. Try adding (use-fixtures :each (system-fixture :test-system-name)) to your test namespace." {}))))

(defn transit-in
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

(defmethod base-request* :transit
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
  (base-request* method url params :transit))

(defn base-request
  ([method url params]
   (base-request* method url params nil))
  ([method url params content-type]
   (base-request* method url params content-type)))

(defn req
  [& args]
  ((handler) (apply base-request args)))

(defn resp-read-transit
  [ring-resp]
  (-> ring-resp
      :body
      (transit/reader :json)
      transit/read))

(defn contains-entity?
  "Request's response data creates entity of type `ent-type` that has
  key/value pairs identical to `test-ent-attrs`"
  [resp-data ent-type test-ent-attrs]
  (let [ent-keys (keys test-ent-attrs)]
    ((->> resp-data
          (filter #(= (first %) :entity))
          (mapcat (comp vals ent-type second))
          (map #(select-keys % ent-keys))
          (set))
     test-ent-attrs)))
