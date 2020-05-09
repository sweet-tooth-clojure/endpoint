(ns sweet-tooth.endpoint.system-test
  (:require [clojure.test :refer [deftest is]]
            [sweet-tooth.endpoint.system :as es]
            [integrant.core :as ig]))

(defmethod ig/init-key ::a [_ opts]
  opts)

(defmethod es/config ::test [_]
  {::a :b})

(deftest system-test
  (is (= {::a :b}
         (es/system ::test))))

(deftest custom-system-test
  (is (= {::a :c}
         (es/system ::test {::a :c})))

  (is (= {::a :c}
         (es/system ::test (fn [cfg] (assoc cfg ::a :c))))))


(defmethod ig/init-key ::b [_ opts]
  {:opts opts})

(defmethod es/config ::replace-test [_]
  {::b :foo})

(deftest replace-component
  (is (= {::b {:opts :foo}}
         (es/system ::replace-test)))

  (is (= {::b {:replacement :component}}
         (es/system ::replace-test {::b ^:replace ^:component {:replacement :component}}))))
