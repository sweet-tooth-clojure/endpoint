(ns sweet-tooth.endpoint.module.liberator-reitit-router-test
  (:require [sweet-tooth.endpoint.module.liberator-reitit-router :as sut]
            [sweet-tooth.endpoint.middleware :as em]
            [sweet-tooth.endpoint.test.harness :as eth]
            [sweet-tooth.endpoint.system :as es]
            [sweet-tooth.endpoint.routes.reitit :as err]

            [ring.mock.request :as mock]
            [duct.core :as duct]
            [integrant.core :as ig]
            [clojure.test :refer :all]))

(duct/load-hierarchy)

(def decisions
  {:list {:handle-ok ["YAY"]}})

(def ns-routes
  (err/expand-routes [[:sweet-tooth.endpoint.module.liberator-reitit-router-test]
                      ["/" {:woo :yeah :handler "x"}]]))

(def duct-config
  {:duct.profile/base {:duct.core/project-ns  'sweet-tooth
                       :duct.core/environment :production}

   :sweet-tooth.endpoint.module/liberator-reitit-router {:routes ::ns-routes}

   :duct.module/logging  {}
   :duct.module.web/api  {}
   :duct.module.web/site {}

   :sweet-tooth.endpoint.module/middleware {}})

(deftest builds-duct-config
  (is (= {::sut/reitit-router [["/module/liberator-reitit-router-test"
                                {:name        :module.liberator-reitit-router-tests
                                 :id-key      :id
                                 :auth-id-key :id
                                 :ent-type    :liberator-reitit-router-test
                                 :middleware  [em/wrap-merge-params]
                                 :handler     (ig/ref ::coll-handler)
                                 ::err/ns     :sweet-tooth.endpoint.module.liberator-reitit-router-test
                                 ::err/type   ::err/coll}]
                               ["/module/liberator-reitit-router-test/{id}"
                                {:name        :module.liberator-reitit-router-test
                                 :id-key      :id
                                 :auth-id-key :id
                                 :ent-type    :liberator-reitit-router-test
                                 :middleware  [em/wrap-merge-params]
                                 :handler     (ig/ref ::unary-handler)
                                 ::err/ns     :sweet-tooth.endpoint.module.liberator-reitit-router-test
                                 ::err/type   ::err/unary}]
                               ["/" {:woo        :yeah
                                     :handler    "x"
                                     :middleware [em/wrap-merge-params]}]]

          ::coll-handler {:name        :module.liberator-reitit-router-tests
                          :id-key      :id
                          :auth-id-key :id
                          :ctx         {:id-key                         :id
                                        :auth-id-key                    :id
                                        :logger                         (ig/ref :duct/logger)
                                        :sweet-tooth.endpoint/namespace :sweet-tooth.endpoint.module.liberator-reitit-router-test}
                          :decisions   'decisions
                          :ent-type    :liberator-reitit-router-test
                          ::err/ns     :sweet-tooth.endpoint.module.liberator-reitit-router-test
                          ::err/type   ::err/coll}

          ::unary-handler {:name        :module.liberator-reitit-router-test
                           :id-key      :id
                           :auth-id-key :id
                           :ctx         {:id-key                         :id
                                         :auth-id-key                    :id
                                         :logger                         (ig/ref :duct/logger)
                                         :sweet-tooth.endpoint/namespace :sweet-tooth.endpoint.module.liberator-reitit-router-test}
                           :decisions   'decisions
                           :ent-type    :liberator-reitit-router-test
                           ::err/ns     :sweet-tooth.endpoint.module.liberator-reitit-router-test
                           ::err/type   ::err/unary}}
         (-> (select-keys (duct/prep-config duct-config) [::sut/reitit-router ::coll-handler ::unary-handler])
             ;; TODO figure out how to not have to do these
             ;; shenanigans

             ;; dropping the first middleware because it's
             ;; an anonymous function so I can't really test for that
             (update ::sut/reitit-router #(mapv (fn [route]
                                                  (update-in route [1 :middleware] (fn [x] (drop 1 x))))
                                                %))))))

(defmethod es/config ::test [_]
  (dissoc (duct/prep-config duct-config) :duct.server.http/jetty))

;; With reitit-routed handlers as the final product of a lot of magic,
;; this test also tests other layers in the system, like gzip
;; middleware
(deftest handler-works
  (let [url "/module/liberator-reitit-router-test"]
    (eth/with-system ::test
      (is (= ["YAY"]
             (eth/resp-read-transit (eth/req :get url))))
      (is (= {"Content-Type"           "application/transit+json"
              "Content-Encoding"       "gzip"
              "Vary"                   "Accept, Accept-Encoding"
              "X-XSS-Protection"       "1; mode=block"
              "X-Frame-Options"        "SAMEORIGIN"
              "X-Content-Type-Options" "nosniff"}
             (-> (eth/base-request :get "/module/liberator-reitit-router-test" {})
                 (mock/header "accept-encoding" "gzip")
                 ((eth/handler))
                 :headers
                 (dissoc "Set-Cookie")))))))

(deftest ns-routes-exception-handling
  (is (#'sut/resolve-ns-routes ::ns-routes))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"Your duct configuration for :sweet-tooth.endpoint.module/liberator-reitit-router is incorrect. Could not find the var specified by :routes."
                        (#'sut/resolve-ns-routes ::blar))))

(deftest default-serves-index-html
  (eth/with-system ::test
    (is (= "hi! i show the fallback route works.\n"
           (slurp (:body ((eth/handler) (mock/request :get "/no-route"))))))))
