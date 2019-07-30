(ns sweet-tooth.endpoint.middleware
  (:require [integrant.core :as ig]
            [duct.core :as duct]
            [ring.middleware.format :as f]
            [sweet-tooth.endpoint.format :as ef]))

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

(defn wrap-format-ctx-body
  [f]
  (fn [req]
    (ef/format-ctx-body (f req))))

(defmethod ig/init-key ::restful-format [_ options]
  #(f/wrap-restful-format % options))

(defmethod ig/init-key ::flush [_ _]
  #(wrap-flush %))

(defmethod ig/init-key ::merge-params [_ _]
  #(wrap-merge-params %))

(defmethod ig/init-key ::format-ctx-body [_ _]
  #(wrap-format-ctx-body %))

(derive :sweet-tooth.endpoint/middleware :duct/module)

(def middleware-config
  {::restful-format  {:formats [:transit-json]}
   ::merge-params    {}
   ::flush           {}
   ::format-ctx-body {}})

(defmethod ig/init-key :sweet-tooth.endpoint/middleware [_ {:keys [middlewares]}]
  (fn [config]
    (let [middlewares (if (empty? middlewares)
                        [::restful-format ::format-ctx-body ::merge-params ::flush]
                        middlewares)]
      (duct/merge-configs
        config
        (reduce (fn [c k]
                  (-> (assoc c k (get middleware-config k))
                      (update-in [:duct.handler/root :middleware] conj (ig/ref k))))
                {:duct.handler/root {:middleware ^:prepend []}}
                middlewares)))))
