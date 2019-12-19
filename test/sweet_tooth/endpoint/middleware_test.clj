(ns sweet-tooth.endpoint.middleware-test
  (:require [sweet-tooth.endpoint.middleware :as em]
            [clojure.test :refer :all]
            [duct.core :as duct]
            [integrant.core :as ig]))

(duct/load-hierarchy)

(deftest group-module
  (is (= {:duct.handler/root   {:middleware [(ig/ref ::em/format-response)
                                             (ig/ref ::em/restful-format)
                                             (ig/ref ::em/merge-params)
                                             (ig/ref ::em/flush)
                                             (ig/ref ::em/gzip)]
                                :router     (ig/ref :duct/router)}
          ::em/gzip            {}
          ::em/restful-format  {:formats [:transit-json]}
          ::em/merge-params    {}
          ::em/format-response {}
          ::em/flush           {}}
         (duct/prep-config {:sweet-tooth.endpoint/middleware {:middlewares []}}))))

(deftest meta-merge-with-default-cofigs
  (is (= {:duct.handler/root {:middleware [(ig/ref :sweet-tooth.endpoint.middleware/restful-format)]
                              :router     (ig/ref :duct/router)}

          :sweet-tooth.endpoint.middleware/restful-format {:formats [:json]}}
         (duct/prep-config {:duct.profile/base               {:sweet-tooth.endpoint.middleware/restful-format {:formats ^:replace [:json]}}
                            :sweet-tooth.endpoint/middleware {:middlewares [::em/restful-format]}}))))
