(ns sweet-tooth.endpoint.format-test
  (:require [sweet-tooth.endpoint.format :as sut]
            [clojure.test :refer [deftest is]]
            [clojure.spec.alpha :as s]))


(deftest formats-entity
  (is (= [[:entity {:topic {3 {:id 3}}}]]
         (sut/format-body ^{:ent-type :topic} {:id 3}
                          :id
                          nil
                          [:entity]))))

(deftest formats-entity-vector
  (is (= [[:entity {:topic {3 {:id 3}}}]]
         (sut/format-body ^{:ent-type :topic} [{:id 3}]
                          :id
                          nil
                          [:entity]))))

(deftest formats-mixed-entity-vector
  (let [body      [^{:ent-type :topic} {:id 3}
                   ^{:ent-type :post}  {:id 4}]
        conformed (s/conform ::sut/raw-response body)]
    (is (= [:unformatted-vector
            [[:entity {:id 3}]
             [:entity {:id 4}]]]
           conformed))
    (is (= [[:entity {:topic {3 {:id 3}}}]
            [:entity {:post {4 {:id 4}}}]]
           (sut/format-body body :id :topic conformed)))))

(deftest formats-segment
  (is (= [[:default {:current-user {}}]]
         (sut/format-body [:default {:current-user {}}]
                          :id
                          nil
                          [:segment]))))

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
             [:segment [:default {:current-user {}}]]]]
           conformed))
    (is (= [[:entity {:topic {3 {:id 3}}}]
            [:default {:current-user {}}]]
           (sut/format-body body
                            :id
                            :topic
                            conformed))))

  (let [body      [{:id 3}
                   [:default {:current-user {}}]
                   ^{:ent-type :post } [{:id 5} {:id 6}]
                   [{:id 7} {:id 8}]
                   [:page {}]]
        conformed (s/conform ::sut/raw-response body)]
    (is (= [:unformatted-vector
            [[:possible-entity {:id 3}]
             [:segment [:default {:current-user {}}]]
             [:entities [{:id 5} {:id 6}]]
             [:possible-entities [{:id 7} {:id 8}]]
             [:segment [:page {}]]]]
           conformed))
    (is (= [[:entity {:topic {3 {:id 3}}}]
            [:default {:current-user {}}]
            [:entity {:post {5 {:id 5}, 6 {:id 6}}}]
            [:entity {:topic {7 {:id 7}, 8 {:id 8}}}]
            [:page {}]]
           (sut/format-body body
                            :id
                            :topic
                            conformed)))))
