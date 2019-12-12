(ns sweet-tooth.endpoint.liberator.reitit-routes-test
  (:require [sweet-tooth.endpoint.liberator.reitit-routes :as sut]
            [sweet-tooth.endpoint.middleware :as em]
            [sweet-tooth.endpoint.test.harness :as eth]
            [sweet-tooth.endpoint.system :as es]
            [sweet-tooth.endpoint.routes.reitit :as err]

            [duct.core :as duct]
            [integrant.core :as ig]
            [clojure.test :refer :all]))

(duct/load-hierarchy)

(def decisions
  {:list {:handle-ok ["YAY"]}})

(def ns-routes
  (err/ns-pairs->ns-routes [[:sweet-tooth.endpoint.liberator.reitit-routes-test]
                            ["/" {:woo :yeah :handler "x"}]]))

(def duct-config
  {:duct.profile/base    {:duct.core/project-ns  'sweet-tooth
                          :duct.core/environment :production}
   ::sut/ns-routes       {:ns-routes ::ns-routes}

   :duct.module/logging  {}
   :duct.module.web/api  {}
   :duct.module.web/site {}})

(deftest builds-duct-config
  (is (= {::sut/router
          [["/liberator/reitit-routes-test"
            {:name        :liberator.reitit-routes-tests
             :id-key      :id
             :auth-id-key :id
             :ent-type    :reitit-routes-test
             :middleware  [em/wrap-merge-params]
             :handler     (ig/ref ::coll-handler)
             :ctx         {:id-key      :id,
                           :auth-id-key :id}
             ::err/ns     :sweet-tooth.endpoint.liberator.reitit-routes-test
             ::err/type   ::err/coll}]
           ["/liberator/reitit-routes-test/{id}"
            {:name        :liberator.reitit-routes-test
             :id-key      :id
             :auth-id-key :id
             :ent-type    :reitit-routes-test
             :middleware  [em/wrap-merge-params]
             :handler     (ig/ref ::unary-handler)
             :ctx         {:id-key      :id,
                           :auth-id-key :id}
             ::err/ns     :sweet-tooth.endpoint.liberator.reitit-routes-test
             ::err/type   ::err/unary}]
           ["/" {:woo        :yeah
                 :handler    "x"
                 :middleware [em/wrap-merge-params]}]]

          ::coll-handler
          {:name        :liberator.reitit-routes-tests
           :id-key      :id
           :auth-id-key :id
           :ctx         {:id-key      :id
                         :auth-id-key :id}
           :ent-type    :reitit-routes-test
           ::err/ns     :sweet-tooth.endpoint.liberator.reitit-routes-test
           ::err/type   ::err/coll}

          ::unary-handler
          {:name        :liberator.reitit-routes-test
           :id-key      :id
           :auth-id-key :id
           :ctx         {:id-key      :id
                         :auth-id-key :id}
           :ent-type    :reitit-routes-test
           ::err/ns     :sweet-tooth.endpoint.liberator.reitit-routes-test
           ::err/type   ::err/unary}}
         (-> (select-keys (duct/prep-config duct-config) [::sut/router ::coll-handler ::unary-handler])
             ;; TODO figure out how to not have to do these
             ;; shenanigans

             ;; dropping the first middleware because it's
             ;; an anonymous function so I can't really test for that
             (update ::sut/router #(mapv (fn [route]
                                           (update-in route [1 :middleware] (fn [x] (drop 1 x))))
                                         %))))))

(defmethod es/config ::test [_]
  (dissoc (duct/prep-config duct-config) :duct.server.http/jetty))

(deftest handler-works
  (eth/with-system ::test
    (is (= ["YAY"]
           (:body (eth/req :get "/liberator/reitit-routes-test"))))))
