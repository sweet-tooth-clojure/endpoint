(ns sweet-tooth.endpoint.routes.reitit-test
  (:require [sweet-tooth.endpoint.routes.reitit :as sut]
            #?@(:clj [[sweet-tooth.endpoint.test.harness :as eth]
                      [sweet-tooth.endpoint.system :as es]
                      [duct.core :as duct]
                      [integrant.core :as ig]])
            #?(:clj [clojure.test :refer :all]
               :cljs [cljs.test :refer :all :include-macros true])))

(duct/load-hierarchy)

(deftest makes-routes
  (is (= [["/user"      {:name      :users
                         ::sut/ns   :ex.endpoint.user
                         ::sut/type ::sut/coll}]
          ["/user/{id}" {:name      :user
                         ::sut/ns   :ex.endpoint.user
                         ::sut/type ::sut/unary}]]
         (sut/ns-pair->ns-route [:ex.endpoint.user]))))

(deftest nested-route
  (is (= [["/admin/user"      {:name      :admin.users
                               ::sut/ns   :ex.endpoint.admin.user
                               ::sut/type ::sut/coll}]
          ["/admin/user/{id}" {:name      :admin.user
                               ::sut/ns   :ex.endpoint.admin.user
                               ::sut/type ::sut/unary}]]
         (sut/ns-pair->ns-route [:ex.endpoint.admin.user]))))

(deftest exclude-route
  (is (= [["/user" {:name      :users
                    ::sut/ns   :ex.endpoint.user
                    ::sut/type ::sut/coll}]]
         (sut/ns-pair->ns-route [:ex.endpoint.user {::sut/unary false}])))

  (is (= [["/user/{id}" {:name      :user
                         ::sut/ns   :ex.endpoint.user
                         ::sut/type ::sut/unary}]]
         (sut/ns-pair->ns-route [:ex.endpoint.user {::sut/coll false}]))))

(deftest common-opts
  (is (= (sut/ns-pairs->ns-routes [{:id-key :db/id}
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

(def decisions
  {:list {:handle-ok ["yay"]}})

(def ns-routes
  (sut/ns-pairs->ns-routes [[:sweet-tooth.endpoint.routes.reitit-test]]))

(def duct-config
  {:duct.profile/base    {:duct.core/project-ns  'sweet-tooth
                          :duct.core/environment :production}
   ::sut/ns-routes       {:ns-routes ::ns-routes}
   
   :duct.module/logging  {}
   :duct.module.web/api  {}
   :duct.module.web/site {}})

(deftest builds-duct-config
  #?(:clj
     (is (= {:sweet-tooth.endpoint.routes.reitit/router
             [["/routes/reitit-test"
               {:name      :routes.reitit-tests
                ::sut/ns   :sweet-tooth.endpoint.routes.reitit-test
                ::sut/type ::sut/coll
                :handler   (ig/ref ::route-handler)}]
              ["/routes/reitit-test/{id}"
               {:name      :routes.reitit-test
                ::sut/ns   :sweet-tooth.endpoint.routes.reitit-test
                ::sut/type ::sut/unary
                :handler   (ig/ref ::route-handler)}]]
             
             ::route-handler
             {:name      :routes.reitit-test
              ::sut/ns   :sweet-tooth.endpoint.routes.reitit-test
              ::sut/type ::sut/unary}}
            (duct/prep-config duct-config)))))

(defmethod es/config ::test [_]
  (dissoc (duct/prep-config duct-config) :duct.server.http/jetty))

(deftest handler-works
  #?(:clj
     (eth/with-system ::test
       ((eth/handler) (eth/req :get "/routes/reitit-test")))))
