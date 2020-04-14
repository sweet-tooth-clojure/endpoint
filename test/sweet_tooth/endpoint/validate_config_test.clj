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

(defn prep-env-config []
  (-> "validate-config/config.edn"
      io/resource
      sut/read-config-with-env-vars
      (duct/prep-config [:duct.profile/dev])))

;;-----
;; env vars
;;-----

(deftest read-config-with-env-vars
  (is (= '{::simple-component     {:duct/env ["SIMPLE_COMPONENT" Str]
                                   :val      nil}
           ::map-component        {:string {:duct/env ["STRING" Str]
                                            :val      nil}
                                   :int    {:duct/env ["INT" Int]
                                            :val      nil}}
           ::included-component   {:k1 {:duct/env ["INCLUDED_INT" Int]
                                        :val      nil}}
           :duct.core/environment :development}
         (prep-env-config))))

(deftest missing-env-vars
  (is (= '[["INCLUDED_INT" Int]
           ["INT" Int]
           ["SIMPLE_COMPONENT" Str]
           ["STRING" Str]]
         (sut/missing-env-vars (prep-env-config)))))

(deftest missing-env-var-config
  (is (= '{::simple-component   {:duct/env ["SIMPLE_COMPONENT" Str]
                                 :val      nil}
           ::map-component      {:string {:duct/env ["STRING" Str]
                                          :val      nil}
                                 :int    {:duct/env ["INT" Int]
                                          :val      nil}}
           ::included-component {:k1 {:duct/env ["INCLUDED_INT" Int]
                                      :val      nil}}}
         (sut/missing-env-var-config (prep-env-config)))))

(deftest missing-env-var-suggested-config
  (is (= '{::simple-component   "SET VALUE HERE"
           ::map-component      {:string "SET VALUE HERE"
                                 :int    "SET VALUE HERE"}
           ::included-component {:k1 "SET VALUE HERE"}}
         (sut/missing-env-var-suggested-config (prep-env-config)))))

(deftest missing-env-var-report
  (is (= "Your config defines these env vars but they have no value:
[\"INCLUDED_INT\" Int] [\"INT\" Int] [\"SIMPLE_COMPONENT\" Str] [\"STRING\" Str]

You can hard-code these in your config with the following:
{:sweet-tooth.endpoint.validate-config-test/simple-component
 \"SET VALUE HERE\",
 :sweet-tooth.endpoint.validate-config-test/map-component
 {:string \"SET VALUE HERE\", :int \"SET VALUE HERE\"},
 :sweet-tooth.endpoint.validate-config-test/included-component
 {:k1 \"SET VALUE HERE\"}}
"
         (sut/missing-env-var-report (prep-env-config)))))

;;-----
;; generic validation
;;-----

(deftest read-config
  (testing "reads the configs without evaling env vars"
    (is (= '{:duct.core/environment :development
             ::simple-component     [env-var "SIMPLE_COMPONENT" Str]
             ::map-component        {:string [env-var "STRING" Str]
                                     :int    [env-var "INT" Int]}
             ::included-component   {:k1 [env-var "INCLUDED_INT" Int]}}
           (prep-raw-config)))))

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
