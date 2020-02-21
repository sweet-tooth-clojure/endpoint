(ns sweet-tooth.endpoint.test.harness
  (:require [cognitect.transit :as transit]
            [integrant.core :as ig]
            [ring.mock.request :as mock]
            [sweet-tooth.endpoint.system :as es]
            [cheshire.core :as json]))

(def ^:dynamic *system* nil)

(defmacro with-system
  "Bind dynamic system vars to a test system."
  [config-name & body]
  `(binding [*system* (es/system ~config-name)]
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
  (:duct.handler/root *system*))

(defn transit-in
  [data]
  (let [out (java.io.ByteArrayOutputStream. 4096)]
    (transit/write (transit/writer out :json) data)
    (java.io.ByteArrayInputStream. (.toByteArray out))))

(defn base-request
  [method url params]
  (-> (mock/request method url)
      (mock/header :content-type "application/transit+json")
      (mock/header :accept "application/transit+json")
      (assoc :body (transit-in params))))

(defn req
  [method url & [params]]
  ((handler) (base-request method url params)))

(defn resp-read-transit
  [ring-resp]
  (-> ring-resp
      :body
      (transit/reader :json)
      transit/read))

(defn json-request
  [method url params]
  (-> (mock/request method url)
      (mock/header :content-type "application/json")
      (mock/header :accept "application/json")
      (assoc :body (json/encode params))))

(defn json-req
  [method url & [params]]
  ((handler) (json-request method url params)))

(defn html-request
  [method url]
  (-> (mock/request method url)
      (mock/header :content-type "text/html")
      (mock/header :accept "text/html")))

(defn html-req
  [method url]
  ((handler) (html-request method url)))

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
