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
         (sut/ns-route [:ex.endpoint.user]))))

(deftest nested-route
  (is (= [["/admin/user"      {:name      :admin.users
                               ::sut/ns   :ex.endpoint.admin.user
                               ::sut/type ::sut/coll}]
          ["/admin/user/{id}" {:name      :admin.user
                               ::sut/ns   :ex.endpoint.admin.user
                               ::sut/type ::sut/unary}]]
         (sut/ns-route [:ex.endpoint.admin.user]))))

(deftest exclude-route
  (is (= [["/user" {:name      :users
                    ::sut/ns   :ex.endpoint.user
                    ::sut/type ::sut/coll}]]
         (sut/ns-route [:ex.endpoint.user {::sut/unary false}])))

  (is (= [["/user/{id}" {:name      :user
                         ::sut/ns   :ex.endpoint.user
                         ::sut/type ::sut/unary}]]
         (sut/ns-route [:ex.endpoint.user {::sut/coll false}]))))

(deftest common-opts
  (is (= (sut/ns-routes [{:id-key :db/id}
                         [:ex.endpoint.user]
                         [:ex.endpoint.topic]])
         
         [["/user" {:name      :users
                    ::sut/ns   :ex.endpoint.user
                    ::sut/type ::sut/coll
                    :id-key    :db/id}]
          ["/user/{db/id}" {:name      :user
                            ::sut/ns   :ex.endpoint.user
                            ::sut/type ::sut/unary
                            :id-key    :db/id}]
          ["/topic" {:name      :topics
                     ::sut/ns   :ex.endpoint.topic
                     ::sut/type ::sut/coll
                     :id-key    :db/id}]
          ["/topic/{db/id}" {:name      :topic
                             ::sut/ns   :ex.endpoint.topic
                             ::sut/type ::sut/unary
                             :id-key    :db/id}]])))
