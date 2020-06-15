(ns sweet-tooth.endpoint.utils-test
  (:require [sweet-tooth.endpoint.utils :as sut]
            #?(:clj [clojure.test :refer [deftest testing is]]
               :cljs [cljs.test :refer [deftest testing is] :include-macros true])))

(deftest handler
  #?(:clj (testing "returns a var for clj"
            (is (= handler (sut/clj-kvar ::handler)))))
  #?(:cljs (testing "returns a keyword for cljs"
             (is (= ::handler (sut/clj-kvar ::handler))))))
