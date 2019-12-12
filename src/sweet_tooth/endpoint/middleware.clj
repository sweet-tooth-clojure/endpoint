(ns sweet-tooth.endpoint.middleware
  (:require [integrant.core :as ig]
            [duct.core :as duct]
            [ring.middleware.format :as f]
            [sweet-tooth.endpoint.format :as ef]
            [clojure.stacktrace :as cst]))

(defn wrap-flush
  "Flush output after each request"
  [f]
  (fn [req]
    (let [res (f req)]
      (flush)
      res)))

(defn wrap-merge-params
  "Some middleware puts params in :body-params. Move it to :params"
  [f]
  (fn [req]
    (let [{:keys [body-params query-params path-params]} req]
      (f (update req :params merge body-params path-params query-params path-params)))))

(defn wrap-format-response
  [f]
  (fn [req]
    (ef/format-response (f req))))

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

(defmethod ig/init-key ::restful-format [_ options]
  #(f/wrap-restful-format % options))

(defmethod ig/init-key ::flush [_ _]
  #(wrap-flush %))

(defmethod ig/init-key ::merge-params [_ _]
  #(wrap-merge-params %))

(defmethod ig/init-key ::format-response [_ _]
  #(wrap-format-response %))

(defmethod ig/init-key ::format-exception [_ opts]
  #(wrap-format-exception % opts))

(derive :sweet-tooth.endpoint/middleware :duct/module)

(def middleware-config
  {::restful-format   {:formats [:transit-json]}
   ::merge-params     {}
   ::flush            {}
   ::format-response  {}
   ::format-exception {:include-data true}})

(defmethod ig/init-key :sweet-tooth.endpoint/middleware [_ {:keys [middlewares]}]
  (fn [config]
    (let [middlewares (filter identity (if (empty? middlewares)
                                         [::format-response
                                          (when (= (:duct.core/environment config) :development) ::format-exception)
                                          ::restful-format
                                          ::merge-params
                                          ::flush]
                                         middlewares))]
      (duct/merge-configs
        (select-keys middleware-config middlewares)
        config
        (reduce (fn [c k]
                  (update-in c [:duct.handler/root :middleware] conj (ig/ref k)))
                {:duct.handler/root {:middleware ^:prepend []}}
                middlewares)))))
