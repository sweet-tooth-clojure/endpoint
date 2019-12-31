(ns sweet-tooth.endpoint.routes.reitit-test
  (:require [sweet-tooth.endpoint.routes.reitit :as sut]

            #?(:clj [clojure.test :refer :all]
               :cljs [cljs.test :refer :all :include-macros true])))

(deftest makes-routes
  (is (= [["/user"      {:name      :users
                         ::sut/ns   :ex.endpoint.user
                         ::sut/type ::sut/coll}]
          ["/user/{id}" {:name      :user
                         ::sut/ns   :ex.endpoint.user
                         ::sut/type ::sut/unary}]]
         (sut/expand-route [:ex.endpoint.user]))))

(deftest nested-route
  (is (= [["/admin/user"      {:name      :admin.users
                               ::sut/ns   :ex.endpoint.admin.user
                               ::sut/type ::sut/coll}]
          ["/admin/user/{id}" {:name      :admin.user
                               ::sut/ns   :ex.endpoint.admin.user
                               ::sut/type ::sut/unary}]]
         (sut/expand-route [:ex.endpoint.admin.user]))))

(deftest exclude-route
  (testing "leaves out a unary or coll route"
    (is (= [["/user" {:name      :users
                      ::sut/ns   :ex.endpoint.user
                      ::sut/type ::sut/coll}]]
           (sut/expand-route [:ex.endpoint.user {::sut/unary false}])))

    (is (= [["/user/{id}" {:name      :user
                           ::sut/ns   :ex.endpoint.user
                           ::sut/type ::sut/unary}]]
           (sut/expand-route [:ex.endpoint.user {::sut/coll false}])))))

(deftest common-opts
  (testing "you can specify common opts and override them"
    (is (= [["/user" {:name      :users
                      ::sut/ns   :ex.endpoint.user
                      ::sut/type ::sut/coll
                      :id-key    :db/id}]
            ["/user/{weird/id}" {:name      :user
                                 ::sut/ns   :ex.endpoint.user
                                 ::sut/type ::sut/unary
                                 :id-key    :weird/id}]
            ["/topic" {:name      :topics
                       ::sut/ns   :ex.endpoint.topic
                       ::sut/type ::sut/coll
                       :id-key    :db/id}]
            ["/topic/{db/id}" {:name      :topic
                               ::sut/ns   :ex.endpoint.topic
                               ::sut/type ::sut/unary
                               :id-key    :db/id}]]
           (sut/expand-routes [{:id-key :db/id}
                               [:ex.endpoint.user {::sut/unary {:id-key :weird/id}}]
                               [:ex.endpoint.topic]])))))

(deftest singleton?
  (testing "singleton? as a shorthand for singleton resources"
    (is (= [["/user" {:name            :user
                      ::sut/ns         :ex.endpoint.user
                      ::sut/type       ::sut/coll
                      ::sut/singleton? true}]]
           (sut/expand-routes [[:ex.endpoint.user {::sut/singleton? true}]])))))
