(ns sweet-tooth.endpoint.test.harness
  (:require [ring.mock.request :as mock]
            [cognitect.transit :as transit]
            [duct.core :as duct]
            [integrant.core :as ig]
            [sweet-tooth.endpoint.system :as es]))

(def ^:dynamic *system* nil)

(defmacro with-system
  "Bind dynamic system vars to a test system."
  [config-name & body]
  `(binding [*system* (es/system ~config-name)]
     (let [return# ~@body]
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
