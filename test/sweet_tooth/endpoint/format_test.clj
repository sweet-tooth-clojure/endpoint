(ns sweet-tooth.endpoint.format-test
  (:require [sweet-tooth.endpoint.format :as sut]
            [clojure.test :refer [deftest is]]
            [clojure.spec.alpha :as s]))


(deftest formats-entity
  (is (= [[:entity {:topic {3 {:id 3}}}]]
         (sut/format-body (with-meta {:id 3} {:ent-type :topic})
                          :id
                          nil
                          [:entity]))))

(deftest formats-entity-vector
  (is (= [[:entity {:topic {3 {:id 3}}}]]
         (sut/format-body (with-meta [{:id 3}] {:ent-type :topic})
                          :id
                          nil
                          [:entity]))))

(deftest formats-item
  (is (= [[:default {:current-user {}}]]
         (sut/format-body [:default {:current-user {}}]
                          :id
                          nil
                          [:item]))))

(deftest returns-formatted-response
  (is (= [[:default {:current-user {}}]
          [:default {:session {}}]]
         (sut/format-body [[:default {:current-user {}}]
                           [:default {:session {}}]]
                          :id
                          nil
                          [:formatted-response]))))

(deftest formats-possible-entity
  (is (= [[:entity {:topic {3 {:id 3}}}]]
         (sut/format-body {:id 3}
                          :id
                          :topic
                          [:possible-entity]))))

(deftest formats-unformatted-vector
  (let [body      [{:id 3} [:default {:current-user {}}]]
        conformed (s/conform ::sut/raw-response body)]
    (is (= [:unformatted-vector
            [[:possible-entity {:id 3}]
             [:item [:default {:current-user {}}]]]]
           conformed))
    (is (= [[:entity {:topic {3 {:id 3}}}]
            [:default {:current-user {}}]]
           (sut/format-body body
                            :id
                            :topic
                            conformed)))))
