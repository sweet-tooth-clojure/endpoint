(ns sweet-tooth.endpoint.middleware-test
  (:require [sweet-tooth.endpoint.middleware :as em]
            [clojure.test :refer :all]
            [duct.core :as duct]
            [integrant.core :as ig]))

(duct/load-hierarchy)

(deftest group-module
  (is (= {:duct.handler/root {:middleware [(ig/ref :sweet-tooth.endpoint.middleware/restful-format)
                                           (ig/ref :sweet-tooth.endpoint.middleware/body-params)
                                           (ig/ref :sweet-tooth.endpoint.middleware/flush)]}
          :sweet-tooth.endpoint.middleware/restful-format {:formats [:transit-json]}
          :sweet-tooth.endpoint.middleware/body-params {}
          :sweet-tooth.endpoint.middleware/flush {}}
         (duct/prep-config {:sweet-tooth.endpoint/middleware {:middlewares []}}))))
