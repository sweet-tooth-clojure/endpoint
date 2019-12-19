(ns sweet-tooth.endpoint.middleware
  (:require [buddy.auth.backends :as backends]
            [buddy.auth.middleware :as buddy]
            [clojure.stacktrace :as cst]
            [duct.core :as duct]
            [integrant.core :as ig]
            [ring.middleware.format :as f]
            [ring.middleware.gzip :as ring-gzip]
            [sweet-tooth.endpoint.format :as ef]))

;;---
;; flush
;;---

(defn wrap-flush
  "Flush output after each request"
  [f]
  (fn [req]
    (let [res (f req)]
      (flush)
      res)))

(defmethod ig/init-key ::flush [_ _]
  #(wrap-flush %))

;;---
;; merge params
;;---

(defn wrap-merge-params
  "Some middleware puts params in :body-params. Move it to :params"
  [f]
  (fn [req]
    (let [{:keys [body-params query-params path-params]} req]
      (f (update req :params merge body-params path-params query-params path-params)))))

(defmethod ig/init-key ::merge-params [_ _]
  #(wrap-merge-params %))

;;---
;; format response
;;---

(defn wrap-format-response
  [f]
  (fn [req]
    (ef/format-response (f req))))

(defmethod ig/init-key ::format-response [_ _]
  #(wrap-format-response %))

;;---
;; format exception
;;---

(defn wrap-format-exception
  "Catches exceptions and returns a formatted response."
  [f {:keys [include-data]}]
  (fn [req]
    (try (f req)
         (catch Throwable t
           {:status 500
            :body   [[:exception (if include-data
                                   {:message (.getMessage t)
                                    :ex-data (ex-data t)
                                    :stack-trace (with-out-str (cst/print-stack-trace t))}
                                   {})]]}))))

(defmethod ig/init-key ::format-exception [_ opts]
  #(wrap-format-exception % opts))


;;---
;; dev CORS
;;---

(defn wrap-dev-cors
  "Configures CORS for sweet tooth's default shadow-cljs setup"
  [handler]
  (fn [req]
    (let [headers {"Access-Control-Allow-Origin" "http://localhost:3000"
                   "Access-Control-Allow-Methods" "GET, PUT, POST, DELETE, OPTIONS"
                   "Access-Control-Allow-Headers" "Content-Type, *"
                   "Access-Control-Allow-Credentials" "true"}]
      (if (= (:request-method req) :options)
        {:status 200 :headers headers :body "preflight complete"}
        (-> (handler req)
            (update :headers merge headers))))))
(defmethod ig/init-key ::dev-cors [_ config]
  #(wrap-dev-cors %))

(defn wrap-latency
  "Introduce latency, useful for local dev when you want to simulate
  more realistic response times"
  [handler {:keys [sleep sleep-max]}]
  (fn [req]
    (Thread/sleep (if sleep-max
                    (rand (+ (- sleep-max sleep) sleep))
                    sleep))
    (handler req)))

(defmethod ig/init-key ::latency [_ opts]
  #(wrap-latency % opts))

;;---
;; integrantized external middleware
;;---

(defmethod ig/init-key ::restful-format [_ options]
  #(f/wrap-restful-format % options))

(defmethod ig/init-key ::buddy-session-auth [_ _]
  #(buddy/wrap-authentication % (backends/session)))

(defmethod ig/init-key ::gzip [_ _]
  #(ring-gzip/wrap-gzip %))

;;---
;; middleware module
;;---

(derive :sweet-tooth.endpoint/middleware :duct/module)

(def middleware-config
  {::gzip             {}
   ::restful-format   {:formats [:transit-json]}
   ::merge-params     {}
   ::flush            {}
   ::format-response  {}
   ::format-exception {:include-data true}})

(def appending-middleware
  #{::gzip})

(defmethod ig/init-key :sweet-tooth.endpoint/middleware [_ {:keys [middlewares]}]
  (fn [config]
    (let [selected-middlewares (filter identity (if (empty? middlewares)
                                                  [::format-response
                                                   ::restful-format
                                                   ::merge-params
                                                   ::flush
                                                   ::gzip]
                                                  middlewares))
          prepend-middlewares  (remove appending-middleware selected-middlewares)
          append-middlewares   (filter appending-middleware selected-middlewares)]
      (duct/merge-configs
        (select-keys middleware-config selected-middlewares)
        config
        {:duct.handler/root {:middleware ^:prepend (mapv ig/ref prepend-middlewares)}}
        {:duct.handler/root {:middleware ^:prepend (mapv ig/ref append-middlewares)}}))))
