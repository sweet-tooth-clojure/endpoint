(ns sweet-tooth.endpoint.middleware
  (:require [clojure.stacktrace :as cst]
            [integrant.core :as ig]
            [reitit.ring.coercion :as rrc]
            [ring.middleware.format :as f]
            [ring.middleware.gzip :as ring-gzip]
            [ring.middleware.stacktrace :as ring-stacktrace]
            [sweet-tooth.endpoint.format :as ef]))

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
  "See `ef/format-response` for an explanation of what this does"
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
                                   {:message     (.getMessage t)
                                    :ex-data     (ex-data t)
                                    :stack-trace (with-out-str (cst/print-stack-trace t))}
                                   {})]]}))))

(defmethod ig/init-key ::format-exception [_ opts]
  #(wrap-format-exception % opts))

;;---
;; debug
;;---

(defn wrap-print
  "Print a request and response. For debugging."
  [f]
  (fn [req]
    (prn ::req req)
    (let [resp (f req)]
      (prn ::resp resp)
      resp)))

(defmethod ig/init-key ::print [_ _]
  #(wrap-print %))

;;---
;; add some latency hey
;;---

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
;; not found - like the default duct but don't force a status
;;---
(defn not-found
  [resp]
  (update resp :status #(or % 404)))

(defn wrap-not-found
  "Middleware that returns a 404 'Not Found' response from an error handler if
  the base handler returns nil."
  [handler error-handler]
  (fn
    ([request]
     (or (handler request) (not-found (error-handler request))))
    ([request respond raise]
     (handler request #(respond (or % (not-found (error-handler request)))) raise))))

(defmethod ig/init-key ::not-found [_ {:keys [error-handler]}]
#(wrap-not-found % error-handler))

;;---
;; integrantized external middleware
;;---

;; serialization and deserialization, see
;; https://github.com/ngrunwald/ring-middleware-format#summary
(defmethod ig/init-key ::restful-format [_ options]
#(f/wrap-restful-format % options))

(defmethod ig/init-key ::gzip [_ _]
#(ring-gzip/wrap-gzip %))

(defmethod ig/init-key ::stacktrace-log [_ options]
#(ring-stacktrace/wrap-stacktrace-log % options))
