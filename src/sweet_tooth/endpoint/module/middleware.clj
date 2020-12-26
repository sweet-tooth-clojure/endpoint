(ns sweet-tooth.endpoint.module.middleware
  (:require [duct.core :as duct]
            [duct.middleware.buddy :as dbuddy]
            [integrant.core :as ig]
            [sweet-tooth.endpoint.handler :as eh]
            [sweet-tooth.endpoint.middleware :as em]))

(def ^:private middleware-config-base
  {::em/gzip               {}
   ::em/restful-format     ^:displace {:formats [:transit-json]}
   ::em/merge-params       {}
   ::em/format-exception   {}
   ::em/stacktrace-log     {}
   ::em/not-found          {:error-handler (ig/ref ::eh/index.html)}
   ::dbuddy/authentication ^:displace {:backend :session}})

(def ^:private middleware-config-dev
  {::em/format-exception ^:displace {:include-data true}})

(def appending-middleware
  #{::em/gzip ::em/format-exception ::em/restful-format ::em/stacktrace-log})

(defmethod ig/init-key :sweet-tooth.endpoint.module/middleware [_ {:keys [exclude]}]
  (fn [{:keys [:duct.core/environment] :as config}]
    (let [selected-middlewares (remove (set exclude) [::em/not-found
                                                      ::dbuddy/authentication
                                                      ::em/merge-params
                                                      ::em/stacktrace-log
                                                      ::em/format-exception
                                                      ::em/restful-format
                                                      ::em/gzip])
          middleware-config    (cond-> middleware-config-base
                                 (#{:development :test} environment) (merge middleware-config-dev))
          prepend-middlewares  (remove appending-middleware selected-middlewares)
          append-middlewares   (filter appending-middleware selected-middlewares)
          config               (update-in config [:duct.handler/root :middleware]
                                          (comp vec (partial remove #{(ig/ref :duct.middleware.web/stacktrace)})))]
      (duct/merge-configs
       (select-keys middleware-config selected-middlewares)
       {::eh/index.html {}}
       config
       {:duct.handler/root {:middleware (with-meta (mapv ig/ref prepend-middlewares) {:prepend true})}}
       {:duct.handler/root {:middleware (mapv ig/ref append-middlewares)}}))))
