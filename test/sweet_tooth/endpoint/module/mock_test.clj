(ns sweet-tooth.endpoint.module.mock-test
  (:require [clojure.test :refer [deftest is]]
            [integrant.core :as ig]
            [sweet-tooth.endpoint.module.mock]
            [sweet-tooth.endpoint.mock :as mock]
            [duct.core :as duct]
            [shrubbery.core :as shrub]))

(duct/load-hierarchy)

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

;;-----
;; using the record returned by the original key
;;-----

(defprotocol Disney
  (see-mickey [_ say]))

(defrecord DisneyTrip []
  Disney
  (see-mickey [_ say] (str "Mickey! " say)))

(defmethod ig/init-key ::defaults-to-record [_ _]
  (DisneyTrip.))

(deftest mocks-a-record
  (let [system         (-> {:duct.profile/base                {[:st/mock ::defaults-to-record] {Disney {:see-mickey "record"}}}
                            :sweet-tooth.endpoint.module/mock {}}
                           duct/prep-config
                           ig/init)
        mock-component (::defaults-to-record-mock system)]
    (= "record" (see-mickey mock-component "hi!"))
    (is (shrub/received? mock-component see-mickey ["hi!"]))))

;;-----
;; specifying an object to mock
;;-----

(defmethod mock/object ::uses-object [_ _]
  (reify Disney
    (see-mickey [_ _] "bloop")))

(deftest uses-object
  (let [system         (-> {:duct.profile/base                {[:st/mock ::uses-object] {Disney {:see-mickey "uses object"}}}
                            :sweet-tooth.endpoint.module/mock {}}
                           duct/prep-config
                           ig/init)
        mock-component (::uses-object-mock system)]
    (is (= "uses object" (see-mickey mock-component "hi!")))
    (is (shrub/received? mock-component see-mickey ["hi!"]))))

;;-----
;; specifying protocols and their mock return values
;;-----

(defmethod mock/protocol-mocks ::uses-proto-mocks [_ _]
  {Disney {:see-mickey "proto mocks"}})

(deftest uses-object
  (let [system         (-> {:duct.profile/base                {[:st/mock ::uses-proto-mocks] {}}
                            :sweet-tooth.endpoint.module/mock {}}
                           duct/prep-config
                           ig/init)
        mock-component (::uses-proto-mocks-mock system)]
    (is (= "proto mocks" (see-mickey mock-component "hi!")))
    (is (shrub/received? mock-component see-mickey ["hi!"]))))
