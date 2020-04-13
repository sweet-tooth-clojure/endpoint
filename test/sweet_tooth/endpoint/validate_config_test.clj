(ns sweet-tooth.endpoint.validate-config-test
  (:require [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest is testing]]
            [duct.core :as duct]
            [sweet-tooth.endpoint.validate-config :as sut]))

(duct/load-hierarchy)

(defn prep-config []
  (-> "validate-config/config.edn"
      io/resource
      duct/read-config
      (duct/prep-config [:duct.profile/dev])))

(defn prep-raw-config []
  (-> "validate-config/config.edn"
      io/resource
      sut/read-config
      (duct/prep-config [:duct.profile/dev])))

(deftest read-config
  (testing "reads the configs without evaling env vars"
    (is (= '{:duct.core/environment :development
             ::simple-component     [env-var "SIMPLE_COMPONENT" Str]
             ::map-component        {:string [env-var "STRING" Str]
                                     :int    [env-var "INT" Int]}
             ::included-component   {:k1 [env-var "INCLUDED_INT" Int]}}
           (prep-raw-config)))))

;;-----
;; validations
;;-----

(s/def ::simple-component some?)

(s/def ::string string?)
(s/def ::int int?)
(s/def ::map-component
  (s/keys :req-un [::string ::int]))

;; use clojure.spec.alpha/explain-str to get validation errors
(defmethod sut/validate-config-key [::simple-component :development]
  [k env config]
  (let [explain (s/explain-str k config)]
    (when (not= "Success!\n" explain)
      {:errors explain})))

(defmethod sut/validate-config-key [::map-component :development]
  [k env config]
  (let [explain (s/explain-str k config)]
    (when (not= "Success!\n" explain)
      {:errors explain})))

(deftest validation-errors
  (let [config     (prep-config)
        raw-config (prep-raw-config)]

    (testing "works with empty configs"
      (is (= []
             (sut/validation-errors {} {}))))

    (testing "Valid configs produce no messages"
      (is (= []
             (sut/validation-errors {::simple-component     "not nil"
                                     :duct.core/environment :development}
                                    raw-config))))

    (testing "Invalid configs produce validation messages"
      (is (= [{:errors        "nil - failed: some? spec: :sweet-tooth.endpoint.validate-config-test/simple-component\n"
               :key           :sweet-tooth.endpoint.validate-config-test/simple-component
               :raw-config    '[env-var "SIMPLE_COMPONENT" Str]
               :actual-config nil}
              {:errors        "nil - failed: string? in: [:string] at: [:string] spec: :sweet-tooth.endpoint.validate-config-test/string\nnil - failed: int? in: [:int] at: [:int] spec: :sweet-tooth.endpoint.validate-config-test/int\n"
               :key           :sweet-tooth.endpoint.validate-config-test/map-component
               :raw-config    '{:string [env-var "STRING" Str], :int [env-var "INT" Int]}
               :actual-config {:string nil
                               :int    nil}}]
             (sut/validation-errors config raw-config))))))
