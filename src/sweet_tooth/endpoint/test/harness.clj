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
  "To be used with `use-fixtures`"
  [config-name]
  (fn [f]
    (with-system config-name
      (f))))

(defn handler
  "The root handler for the system. Used to perform requests."
  []
  (or (:duct.handler/root *system*)
      (throw (ex-info "No request handler for *system*. Try adding (use-fixtures :each (system-fixture :test-system-name)) to your test namespace." {}))))

(defn transit-in
  "An input stream of json-enccoded transit. For request bodies."
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

(defn base-request
  ([method url]
   (base-request* method url {} nil))
  ([method url params-or-content-type]
   (if (keyword? params-or-content-type)
     (base-request* method url {} params-or-content-type)
     (base-request* method url params-or-content-type nil)))
  ([method url params content-type]
   (base-request* method url params content-type)))

(defn req
  [& args]
  ((handler) (apply base-request args)))

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
