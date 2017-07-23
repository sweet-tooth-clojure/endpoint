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

(defmethod ig/init-key :sweet-tooth.endpoint/middleware [_ _]
  {:req #{:duct.core/handler}
   :fn (fn [config]
         (duct/merge-configs
           config
           {:duct.core/handler {:middleware ^:prepend [(ig/ref :sweet-tooth.endpoint.middleware/restful-format)
                                                       (ig/ref :sweet-tooth.endpoint.middleware/body-params)
                                                       (ig/ref :sweet-tooth.endpoint.middleware/flush)]}
            :sweet-tooth.endpoint.middleware/restful-format {:formats [:transit-json]}
            :sweet-tooth.endpoint.middleware/body-params    {}
            :sweet-tooth.endpoint.middleware/flush          {}}))})
