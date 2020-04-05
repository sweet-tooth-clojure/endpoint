(ns sweet-tooth.endpoint.test.harness-test
  (:require [clojure.test :refer [deftest is]]
            [sweet-tooth.endpoint.system :as es]
            [sweet-tooth.endpoint.test.harness :as eth]
            [integrant.core :as ig]))

(defmethod ig/init-key ::a [_ opts]
  opts)

(defmethod ig/init-key ::b [_ opts]
  opts)

(defmethod es/config ::test [_]
  {::a (ig/ref ::b)
   ::b "hi"})

(deftest with-system-test
  (is (= {::a "hi" ::b "hi"}
         (eth/with-system ::test
           eth/*system*))))

(deftest with-custom-system-test
  (is (= {::a :c ::b "hi"}
         (eth/with-custom-system ::test
           {::a :c}
           eth/*system*)))

  (is (= {::a :c ::b "hi"}
         (eth/with-custom-system ::test
           (fn [cfg] (assoc cfg ::a :c))
           eth/*system*))))
