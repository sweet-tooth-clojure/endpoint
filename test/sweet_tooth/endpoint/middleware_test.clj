(ns sweet-tooth.endpoint.middleware-test
  (:require [sweet-tooth.endpoint.middleware :as em]
            [clojure.test :refer :all]
            [duct.core :as duct]
            [integrant.core :as ig]))

(duct/load-hierarchy)

(deftest group-module
  (is (= {:duct.handler/root {:middleware [(ig/ref :sweet-tooth.endpoint.middleware/format-response)
                                           (ig/ref :sweet-tooth.endpoint.middleware/restful-format)
                                           (ig/ref :sweet-tooth.endpoint.middleware/merge-params)
                                           (ig/ref :sweet-tooth.endpoint.middleware/flush)]
                              :router     (ig/ref :duct/router)}

          :sweet-tooth.endpoint.middleware/format-response {}
          :sweet-tooth.endpoint.middleware/restful-format  {:formats [:transit-json]}
          :sweet-tooth.endpoint.middleware/merge-params    {}
          :sweet-tooth.endpoint.middleware/flush           {}}
         (duct/prep-config {:sweet-tooth.endpoint/middleware {:middlewares []}}))))

(deftest meta-merge-with-default-cofigs
  (is (= {:duct.handler/root {:middleware [(ig/ref :sweet-tooth.endpoint.middleware/restful-format)]
                              :router     (ig/ref :duct/router)}

          :sweet-tooth.endpoint.middleware/restful-format {:formats [:json]}}
         (duct/prep-config {:duct.profile/base               {:sweet-tooth.endpoint.middleware/restful-format {:formats ^:replace [:json]}}
                            :sweet-tooth.endpoint/middleware {:middlewares [::em/restful-format]}}))))
