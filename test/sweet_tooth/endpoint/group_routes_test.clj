(ns sweet-tooth.endpoint.group-routes-test
  (:require [sweet-tooth.endpoint.group-routes :as eg]
            [clojure.test :refer :all]
            [duct.core :as duct]
            [integrant.core :as ig]))

(duct/load-hierarchy)

(deftest group-module
  (is (= {:foo/bar {}
          :duct.router/cascading [(ig/ref :a/b) (ig/ref :a/c) (ig/ref :a/d)]
          :a/b (ig/ref :foo/bar)
          :a/c (ig/ref :foo/bar)
          :a/d (ig/ref :foo/bar)}
         (duct/prep-config {:duct.profile/base {:foo/bar {}}
                            :sweet-tooth.endpoint/group-routes {[:a/b :a/c :a/d]
                                                                (ig/ref :foo/bar)}}))))
