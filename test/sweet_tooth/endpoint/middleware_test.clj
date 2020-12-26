(ns sweet-tooth.endpoint.middleware-test
  (:require [clojure.test :refer [deftest is]]
            [duct.core :as duct]
            [duct.middleware.buddy :as dbuddy]
            [integrant.core :as ig]
            [sweet-tooth.endpoint.middleware :as em]))

(duct/load-hierarchy)

(deftest meta-merge-with-default-configs
  (is (= {:sweet-tooth.endpoint.middleware/stacktrace-log {}
          :sweet-tooth.endpoint.middleware/restful-format {:formats [:json]}
          :sweet-tooth.endpoint.middleware/not-found      {:error-handler (ig/ref :sweet-tooth.endpoint.handler/index.html)}
          :sweet-tooth.endpoint.handler/index.html        {}
          :duct.handler/root                              {:router     (ig/ref :duct/router)
                                                           :middleware [(ig/ref :sweet-tooth.endpoint.middleware/not-found)
                                                                        (ig/ref :sweet-tooth.endpoint.middleware/stacktrace-log)
                                                                        (ig/ref :sweet-tooth.endpoint.middleware/restful-format)]}}

         (duct/prep-config {:duct.profile/base                      {:sweet-tooth.endpoint.middleware/restful-format {:formats ^:replace [:json]}}
                            :sweet-tooth.endpoint.module/middleware {:exclude [::em/gzip
                                                                               ::em/merge-params
                                                                               ::em/format-exception
                                                                               ::dbuddy/authentication]}}))))
