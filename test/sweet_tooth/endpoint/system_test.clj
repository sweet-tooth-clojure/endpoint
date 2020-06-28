(ns sweet-tooth.endpoint.system-test
  (:require [clojure.test :refer [deftest is testing]]
            [sweet-tooth.endpoint.system :as es]
            [integrant.core :as ig]
            [shrubbery.core :as shrub]
            [clojure.spec.alpha :as s]))

(s/check-asserts true)

(defmethod ig/init-key ::a [_ opts]
  opts)

(defmethod ig/init-key ::boop [_ opts]
  opts)

(defmethod es/config ::test [_]
  {::a :b})

;;------
;; system function
;;------

(deftest system-test
  (is (= {::a :b}
         (es/system ::test))))

(deftest custom-system-test
  (is (= {::a :c}
         (es/system ::test {::a :c})))

  (is (= {::a :c}
         (es/system ::test (fn [cfg] (assoc cfg ::a :c))))))

(deftest system-init-keys
  (is (= {::boop :boop}
         (es/system ::test {::boop :boop} [::boop]))))

;;------
;; alternative component impls
;;------
(s/def ::foo map?)

(deftest component-spec-with-alternative
  (let [spec (es/component-spec-with-alternative ::foo)]
    (is (s/valid? spec (es/shrubbery-mock)))
    (is (s/valid? spec (es/replacement {})))
    (is (s/valid? spec {}))
    (is (not (s/valid? spec nil)))))

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


(deftest shrubbery-mock
  (is (= {::es/init-key-alternative :sweet-tooth.endpoint.system/shrubbery-mock
          ::es/shrubbery-mock       {:foo :bar}
          :baz                      :boop}
         (es/shrubbery-mock {:foo                       :bar
                             ::es/mocked-component-opts {:baz :boop}})))
  (is (= {::es/init-key-alternative :sweet-tooth.endpoint.system/shrubbery-mock
          ::es/shrubbery-mock       {:foo :bar}
          :baz                      :boop}
         (es/shrubbery-mock [{:foo :bar}
                             {:baz :boop}]))))

(es/system ::alternative-test {::b (es/shrubbery-mock {Stubby {:blurm "blurmed!"}})})
