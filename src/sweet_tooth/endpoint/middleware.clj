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

(defn wrap-body-params
  "Some middleware puts params in :body-params. Move it to :params"
  [f]
  (fn [req]
    (f (if-let [bp (:body-params req)]
         (assoc req :params bp)
         req))))

(defmethod ig/init-key ::restful-format [_ options]
  #(f/wrap-restful-format % options))

(defmethod ig/init-key ::flush [_ _]
  #(wrap-flush %))

(defmethod ig/init-key ::body-params [_ _]
  #(wrap-body-params %))

(derive :sweet-tooth.endpoint/middleware :duct/module)

(def middleware-config
  {::restful-format {:formats [:transit-json]}
   ::body-params    {}
   ::flush          {}})

(defmethod ig/init-key :sweet-tooth.endpoint/middleware [_ {:keys [middlewares]}]
  (fn [config]
    (let [middlewares (if (empty? middlewares)
                        [::restful-format ::body-params ::flush]
                        middlewares)]
      (duct/merge-configs
        config
        (reduce (fn [c k]
                  (-> (assoc c k (get middleware-config k))
                      (update-in [:duct.handler/root :middleware] conj (ig/ref k))))
                {:duct.handler/root {:middleware ^:prepend []}}
                middlewares)))))
