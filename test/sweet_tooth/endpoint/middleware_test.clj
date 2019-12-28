(ns sweet-tooth.endpoint.middleware-test
  (:require [sweet-tooth.endpoint.middleware :as em]
            [clojure.test :refer :all]
            [duct.core :as duct]
            [integrant.core :as ig]))

(duct/load-hierarchy)

(deftest meta-merge-with-default-configs
  (is (= {:duct.handler/root {:middleware [(ig/ref :sweet-tooth.endpoint.middleware/restful-format)]
                              :router     (ig/ref :duct/router)}

          :sweet-tooth.endpoint.middleware/restful-format {:formats [:json]}}
         (duct/prep-config {:duct.profile/base               {:sweet-tooth.endpoint.middleware/restful-format {:formats ^:replace [:json]}}
                            :sweet-tooth.endpoint/middleware {:middlewares [::em/restful-format]}}))))
