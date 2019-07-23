(ns sweet-tooth.endpoint.middleware
  (:require [integrant.core :as ig]
            [duct.core :as duct]
            [ring.middleware.format :as f]))

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

(defmethod ig/init-key ::restful-format [_ options]
  #(f/wrap-restful-format % options))

(defmethod ig/init-key ::flush [_ _]
  #(wrap-flush %))

(defmethod ig/init-key ::merge-params [_ _]
  #(wrap-merge-params %))

(derive :sweet-tooth.endpoint/middleware :duct/module)

(def middleware-config
  {::restful-format {:formats [:transit-json]}
   ::merge-params   {}
   ::flush          {}})

(defmethod ig/init-key :sweet-tooth.endpoint/middleware [_ {:keys [middlewares]}]
  (fn [config]
    (let [middlewares (if (empty? middlewares)
                        [::restful-format ::merge-params ::flush]
                        middlewares)]
      (duct/merge-configs
        config
        (reduce (fn [c k]
                  (-> (assoc c k (get middleware-config k))
                      (update-in [:duct.handler/root :middleware] conj (ig/ref k))))
                {:duct.handler/root {:middleware ^:prepend []}}
                middlewares)))))
