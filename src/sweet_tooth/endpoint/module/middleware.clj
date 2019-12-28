(ns sweet-tooth.endpoint.module.middleware
  (:require [duct.core :as duct]
            [integrant.core :as ig]
            [sweet-tooth.endpoint.middleware :as em]))

(def middleware-config
  {::em/gzip             {}
   ::em/restful-format   {:formats [:transit-json]}
   ::em/merge-params     {}
   ::em/flush            {}
   ::em/format-response  {}
   ::em/format-exception {:include-data true}})

(def appending-middleware
  #{::em/gzip})

(defmethod ig/init-key :sweet-tooth.endpoint.module/middleware [_ {:keys [middlewares]}]
  (fn [config]
    (let [selected-middlewares (filter identity (if (empty? middlewares)
                                                  [::em/format-response
                                                   ::em/restful-format
                                                   ::em/merge-params
                                                   ::em/flush
                                                   ::em/gzip]
                                                  middlewares))
          prepend-middlewares  (remove appending-middleware selected-middlewares)
          append-middlewares   (filter appending-middleware selected-middlewares)]
      (duct/merge-configs
        (select-keys middleware-config selected-middlewares)
        config
        {:duct.handler/root {:middleware (with-meta (mapv ig/ref prepend-middlewares) {:prepend true})}}
        {:duct.handler/root {:middleware (mapv ig/ref append-middlewares)}}))))
