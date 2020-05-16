(ns sweet-tooth.endpoint.system-test
  (:require [clojure.test :refer [deftest is testing]]
            [sweet-tooth.endpoint.system :as es]
            [integrant.core :as ig]
            [shrubbery.core :as shrub]))

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


;;------
;; alternative component impls
;;------s

(defprotocol Stubby
  (blurm [_]))

(defrecord TestRecord []
  Stubby)

(defmethod ig/init-key ::b [_ opts]
  (map->TestRecord opts))

(defmethod es/config ::alternative-test [_]
  {::b {:foo :bar}})

(deftest replace-component
  (is (= {::b (map->TestRecord {:foo :bar})}
         (es/system ::alternative-test)))

  (is (= {::b {:replacement :component}}
         (es/system ::alternative-test {::b (es/replacement {:replacement :component})})))

  (let [system (es/system ::alternative-test {::b (es/replacement (shrub/stub Stubby {:blurm "blurmed!"}))})]
    (is (= "blurmed!" (blurm (::b system))))))

(deftest mock-component
  (testing "works with protocol specified"
    (is (= "blurmed!" (blurm (::b (es/system ::alternative-test {::b (es/shrubbery-mock {Stubby {:blurm "blurmed!"}})}))))))

  (testing "works without protocol specified"
    (is (= "blurmed!" (blurm (::b (es/system ::alternative-test {::b (es/shrubbery-mock {:blurm "blurmed!"})}))))))

  (testing "works with no methods specified"
    (let [{:keys [::b]} (es/system ::alternative-test {::b (es/shrubbery-mock)})]
      (blurm b)
      (is (shrub/received? b blurm [])))))
