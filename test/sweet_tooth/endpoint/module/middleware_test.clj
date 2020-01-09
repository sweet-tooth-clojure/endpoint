(ns sweet-tooth.endpoint.module.middleware-test
  (:require [clojure.test :refer [deftest is]]
            [duct.core :as duct]
            [duct.middleware.buddy :as dbuddy]
            [duct.middleware.web :as dweb]
            [integrant.core :as ig]
            [sweet-tooth.endpoint.middleware :as em]
            [sweet-tooth.endpoint.handler :as eh]))

(duct/load-hierarchy)

(deftest module-groups-middleware-in-correct-order
  (is (= {:duct.core/environment  :test
          :duct.handler/root      {:middleware [(ig/ref ::dbuddy/authentication)
                                                (ig/ref ::em/format-response)
                                                (ig/ref ::em/format-exception)
                                                (ig/ref ::em/restful-format)
                                                (ig/ref ::em/merge-params)
                                                (ig/ref ::null)
                                                (ig/ref ::em/gzip)]
                                   :router     (ig/ref :duct/router)}
          ::dweb/not-found        {:error-handler (ig/ref ::eh/index.html)}
          ::em/format-exception   {:include-data true}
          ::em/gzip               {}
          ::em/restful-format     {:formats [:transit-json]}
          ::em/merge-params       {}
          ::em/format-response    {}
          ::dbuddy/authentication {:backend :session}
          ::eh/index.html         {}}
         (duct/prep-config {:duct.profile/base                      {:duct.handler/root     {:middleware [(ig/ref ::null)]}
                                                                     :duct.core/environment :test}
                            :sweet-tooth.endpoint.module/middleware {:middlewares []}}))))

(deftest exclude-middleware
  (is (= {:duct.core/environment :test
          :duct.handler/root     {:middleware [(ig/ref ::em/format-response)
                                               (ig/ref ::em/format-exception)
                                               (ig/ref ::em/merge-params)
                                               (ig/ref ::null)
                                               (ig/ref ::em/gzip)]
                                  :router     (ig/ref :duct/router)}
          ::dweb/not-found       {:error-handler (ig/ref ::eh/index.html)}
          ::em/format-exception  {:include-data true}
          ::em/gzip              {}
          ::em/merge-params      {}
          ::em/format-response   {}
          ::eh/index.html        {}}
         (duct/prep-config {:duct.profile/base                      {:duct.handler/root     {:middleware [(ig/ref ::null)]}
                                                                     :duct.core/environment :test}
                            :sweet-tooth.endpoint.module/middleware {:exclude [::dbuddy/authentication ::em/restful-format]}}))))
