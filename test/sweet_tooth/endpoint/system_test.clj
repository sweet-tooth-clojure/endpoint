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
