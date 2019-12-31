(ns sweet-tooth.endpoint.module.middleware-test
  (:require [sweet-tooth.endpoint.middleware :as em]
            [clojure.test :refer [deftest is]]
            [duct.core :as duct]
            [integrant.core :as ig]))

(duct/load-hierarchy)

(deftest module-groups-middleware-in-correct-order
  (is (= {:duct.handler/root   {:middleware [(ig/ref ::em/format-response)
                                             (ig/ref ::em/restful-format)
                                             (ig/ref ::em/merge-params)
                                             (ig/ref ::null)
                                             (ig/ref ::em/gzip)]
                                :router     (ig/ref :duct/router)}
          ::em/gzip            {}
          ::em/restful-format  {:formats [:transit-json]}
          ::em/merge-params    {}
          ::em/format-response {}
          ::null               {}}
         (duct/prep-config {:duct.profile/base                      {:duct.handler/root {:middleware [(ig/ref ::null)]}
                                                                     ::null             {}}
                            :sweet-tooth.endpoint.module/middleware {:middlewares []}}))))
