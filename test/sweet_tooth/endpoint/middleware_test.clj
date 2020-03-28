(ns sweet-tooth.endpoint.middleware-test
  (:require [clojure.test :refer [deftest is]]
            [duct.core :as duct]
            [duct.middleware.buddy :as dbuddy]
            [integrant.core :as ig]
            [sweet-tooth.endpoint.middleware :as em]))

(duct/load-hierarchy)

(deftest meta-merge-with-default-configs
  (is (= {:duct.handler/root {:middleware [(ig/ref :sweet-tooth.endpoint.middleware/restful-format)]
                              :router     (ig/ref :duct/router)}

          :sweet-tooth.endpoint.middleware/restful-format {:formats [:json]}
          :duct.middleware.web/not-found                  {:error-handler (ig/ref :sweet-tooth.endpoint.handler/index.html)},
          :sweet-tooth.endpoint.handler/index.html        {}}

         (duct/prep-config {:duct.profile/base                      {:sweet-tooth.endpoint.middleware/restful-format {:formats ^:replace [:json]}}
                            :sweet-tooth.endpoint.module/middleware {:exclude [::em/gzip
                                                                               ::em/merge-params
                                                                               ::em/format-response
                                                                               ::em/format-exception
                                                                               ::dbuddy/authentication]}}))))
