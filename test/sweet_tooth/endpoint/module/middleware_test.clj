(ns sweet-tooth.endpoint.module.middleware-test
  (:require [clojure.test :refer [deftest is]]
            [duct.core :as duct]
            [duct.middleware.buddy :as dbuddy]
            [integrant.core :as ig]
            [sweet-tooth.endpoint.middleware :as em]
            [sweet-tooth.endpoint.handler :as eh]))

(duct/load-hierarchy)

(deftest module-groups-middleware-in-correct-order
  (is (= {:duct.core/environment  :test
          :duct.handler/root      {:middleware [(ig/ref :sweet-tooth.endpoint.middleware/not-found)
                                                (ig/ref :duct.middleware.buddy/authentication)
                                                (ig/ref :sweet-tooth.endpoint.middleware/format-response)
                                                (ig/ref :sweet-tooth.endpoint.middleware/merge-params)
                                                (ig/ref :sweet-tooth.endpoint.module.middleware-test/null)
                                                (ig/ref :sweet-tooth.endpoint.middleware/stacktrace-log)
                                                (ig/ref :sweet-tooth.endpoint.middleware/format-exception)
                                                (ig/ref :sweet-tooth.endpoint.middleware/restful-format)
                                                (ig/ref :sweet-tooth.endpoint.middleware/gzip)]
                                   :router     (ig/ref :duct/router)}
          ::em/not-found          {:error-handler (ig/ref ::eh/index.html)}
          ::em/format-exception   {:include-data true}
          ::em/gzip               {}
          ::em/restful-format     {:formats [:transit-json]}
          ::em/merge-params       {}
          ::em/format-response    {}
          ::em/stacktrace-log     {}
          ::dbuddy/authentication {:backend :session}
          ::eh/index.html         {}}
         (duct/prep-config {:duct.profile/base                      {:duct.handler/root     {:middleware [(ig/ref ::null)]}
                                                                     :duct.core/environment :test}
                            :sweet-tooth.endpoint.module/middleware {:middlewares []}}))))

(deftest exclude-middleware
  (is (= {:duct.core/environment :test
          :duct.handler/root     {:middleware [(ig/ref :sweet-tooth.endpoint.middleware/not-found)
                                               (ig/ref :sweet-tooth.endpoint.middleware/format-response)
                                               (ig/ref :sweet-tooth.endpoint.middleware/merge-params)
                                               (ig/ref :sweet-tooth.endpoint.module.middleware-test/null)
                                               (ig/ref :sweet-tooth.endpoint.middleware/stacktrace-log)
                                               (ig/ref :sweet-tooth.endpoint.middleware/format-exception)
                                               (ig/ref :sweet-tooth.endpoint.middleware/gzip)]
                                  :router     (ig/ref :duct/router)}
          ::em/not-found         {:error-handler (ig/ref ::eh/index.html)}
          ::em/format-exception  {:include-data true}
          ::em/gzip              {}
          ::em/merge-params      {}
          ::em/format-response   {}
          ::em/stacktrace-log    {}
          ::eh/index.html        {}}
         (duct/prep-config {:duct.profile/base                      {:duct.handler/root     {:middleware [(ig/ref ::null)]}
                                                                     :duct.core/environment :test}
                            :sweet-tooth.endpoint.module/middleware {:exclude [::dbuddy/authentication ::em/restful-format]}}))))
