(ns sweet-tooth.endpoint.module.mock-test
  (:require [sweet-tooth.endpoint.module.mock :as sut]
            [clojure.test :refer [deftest is]]
            [integrant.core :as ig]))

(def mock-config
  {[:st/mock :foo/bar] {:a :b}
   [:st/mock :foo/baz] {:c :d}
   :foo/bar            {}
   :foo/not-mocked     {:e :f}})

(deftest mock-module-produces-mock-config
  (is (= {:foo/bar-mock   {:a :b}
          :foo/baz-mock   {:c :d}
          :foo/not-mocked {:e :f}}
         ((ig/init-key :sweet-tooth.endpoint.module/mock {}) mock-config))))

