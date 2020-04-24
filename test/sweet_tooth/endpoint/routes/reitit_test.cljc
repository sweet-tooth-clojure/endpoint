(ns sweet-tooth.endpoint.routes.reitit-test
  (:require [sweet-tooth.endpoint.routes.reitit :as sut]
            #?(:clj [clojure.test :refer :all]
               :cljs [cljs.test :refer :all :include-macros true])))

(deftest makes-routes
  (is (= [["/user"      {:name      :users
                         ::sut/ns   :ex.endpoint.user
                         ::sut/type :coll}]
          ["/user/{id}" {:name      :user
                         ::sut/ns   :ex.endpoint.user
                         ::sut/type :ent
                         :id-key    :id}]]
         (sut/expand-route [:ex.endpoint.user]))))

(deftest nested-route
  (is (= [["/admin/user"      {:name      :admin.users
                               ::sut/ns   :ex.endpoint.admin.user
                               ::sut/type :coll}]
          ["/admin/user/{id}" {:name      :admin.user
                               ::sut/ns   :ex.endpoint.admin.user
                               ::sut/type :ent
                               :id-key    :id}]]
         (sut/expand-route [:ex.endpoint.admin.user]))))

(deftest exclude-route
  (testing "if you specify route types only those are included"
    (is (= [["/user" {:name      :users
                      ::sut/ns   :ex.endpoint.user
                      ::sut/type :coll}]]
           (sut/expand-route [:ex.endpoint.user {::sut/expand-with [:coll]}])))

    (is (= [["/user/{id}" {:name      :user
                           ::sut/ns   :ex.endpoint.user
                           ::sut/type :ent
                           :id-key    :id}]]
           (sut/expand-route [:ex.endpoint.user {::sut/expand-with [:ent]}])))))

(deftest common-opts
  (testing "you can specify common opts and override them"
    (is (= [["/user" {:name      :users
                      ::sut/ns   :ex.endpoint.user
                      ::sut/type :coll
                      :id-key    :weird/id}]
            ["/user/{weird/id}" {:name      :user
                                 ::sut/ns   :ex.endpoint.user
                                 ::sut/type :ent
                                 :id-key    :weird/id}]
            ["/topic" {:name      :topics
                       ::sut/ns   :ex.endpoint.topic
                       ::sut/type :coll
                       :id-key    :db/id}]
            ["/topic/{db/id}" {:name      :topic
                               ::sut/ns   :ex.endpoint.topic
                               ::sut/type :ent
                               :id-key    :db/id}]]
           (sut/expand-routes [{:id-key :db/id}
                               [:ex.endpoint.user {:id-key :weird/id}]
                               [:ex.endpoint.topic]])))))

(deftest shared-opts
  (testing "opts are shared across unary and coll"
    (is (= [["/user" {:name      :users
                      ::sut/ns   :ex.endpoint.user
                      ::sut/type :coll
                      :a         :b}]
            ["/user/{id}" {:name      :user
                           ::sut/ns   :ex.endpoint.user
                           ::sut/type :ent
                           :a         :b
                           :id-key    :id}]]
           (sut/expand-routes [[:ex.endpoint.user {:a :b}]])))))

(deftest paths
  (testing "custom path construction"
    (is (= [["/boop" {:name      :users
                      ::sut/ns   :ex.endpoint.user
                      ::sut/type :coll}]
            ["/boop" {:name      :user
                      ::sut/ns   :ex.endpoint.user
                      ::sut/type :ent
                      :id-key    :id}]]
           (sut/expand-routes [[:ex.endpoint.user {::sut/path "/boop"}]])))

    (is (= [["/user/x" {:name      :users
                        ::sut/ns   :ex.endpoint.user
                        ::sut/type :coll}]
            ["/user/{id}/x" {:name      :user
                             ::sut/ns   :ex.endpoint.user
                             ::sut/type :ent
                             :id-key    :id}]]
           (sut/expand-routes [[:ex.endpoint.user {::sut/path-suffix "/x"}]])))))

(deftest singleton
  (is (= [["/user" {:name      :user
                    ::sut/ns   :ex.endpoint.user
                    ::sut/type :singleton}]]
         (sut/expand-routes [[:ex.endpoint.user {::sut/expand-with [:singleton]}]]))))


(deftest expand-route
  (testing "default handling of unknown route types"
    (is (= [["/user/{id}/boop" {:name      :user.boop
                                ::sut/ns   :ex.endpoint.user
                                ::sut/type :boop
                                :id-key    :id}]
            ["/user/{id}/moop" {:name      :user.moop
                                ::sut/ns   :ex.endpoint.user
                                ::sut/type :moop
                                :id-key    :id}]]
           (sut/expand-route [:ex.endpoint.user {::sut/expand-with [:boop :moop]}]))))

  (testing "respects id key for unknown route types"
    (is (= [["/user/{oop/id}/boop" {:name  :user.boop
                                    ::sut/ns   :ex.endpoint.user
                                    ::sut/type :boop
                                    :id-key    :oop/id}]
            ["/user/{oop/id}/moop" {:name  :user.moop
                                    ::sut/ns   :ex.endpoint.user
                                    ::sut/type :moop
                                    :id-key    :oop/id}]]
           (sut/expand-route [:ex.endpoint.user {::sut/expand-with [:boop :moop]
                                                 :id-key      :oop/id}])))))
