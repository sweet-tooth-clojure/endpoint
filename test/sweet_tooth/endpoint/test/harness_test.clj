(ns sweet-tooth.endpoint.test.harness-test
  (:require [clojure.test :refer [deftest is]]
            [sweet-tooth.endpoint.system :as es]
            [sweet-tooth.endpoint.test.harness :as eth]
            [integrant.core :as ig]))

(defmethod ig/init-key ::a [_ opts]
  opts)

(defmethod ig/init-key ::b [_ opts]
  opts)

(defmethod es/config ::test [_]
  {::a (ig/ref ::b)
   ::b "hi"})

(deftest with-system-test
  (is (= {::a "hi" ::b "hi"}
         (eth/with-system ::test
           eth/*system*))))

(deftest with-custom-system-test
  (is (= {::a :c ::b "hi"}
         (eth/with-custom-system ::test
           {::a :c}
           eth/*system*)))

  (is (= {::a :c ::b "hi"}
         (eth/with-custom-system ::test
           (fn [cfg] (assoc cfg ::a :c))
           eth/*system*))))

(deftest base-request-html
  (is (= {:server-port    80
          :server-name    "localhost"
          :remote-addr    "localhost"
          :uri            "/"
          :query-string   ""
          :scheme         :http
          :request-method :get
          :headers        {"host" "localhost"}}
         (eth/base-request :get "/" :html)))

  (let [req (eth/base-request :post "/" {:x "y"} :html)]
    (is (= {:remote-addr    "localhost"
            :headers        {"host"           "localhost"
                             "content-type"   "application/x-www-form-urlencoded"
                             "content-length" "3"}
            :server-port    80
            :content-length 3
            :content-type   "application/x-www-form-urlencoded"
            :uri            "/"
            :server-name    "localhost"
            :query-string   nil
            :scheme         :http
            :request-method :post}
           (dissoc req :body)))

    (is (= "x=y" (eth/read-body req)))))

(deftest base-request-transit
  (let [req (eth/base-request :get "/" :transit)]
    (is (= {:remote-addr    "localhost"
            :headers        {"host"         "localhost"
                             "content-type" "application/transit+json"
                             "accept"       "application/transit+json"}
            :server-port    80
            :uri            "/"
            :server-name    "localhost"
            :query-string   nil
            :scheme         :http
            :request-method :get}
           (dissoc req :body)))
    (is (= {}
           (eth/read-body req))))

  (let [req (eth/base-request :post "/" {:x :y} :transit)]
    (is (= {:remote-addr    "localhost"
            :headers        {"host"         "localhost"
                             "content-type" "application/transit+json"
                             "accept"       "application/transit+json"}
            :server-port    80
            :uri            "/"
            :server-name    "localhost"
            :query-string   nil
            :scheme         :http
            :request-method :post}
           (dissoc req :body)))
    (is (= {:x :y}
           (eth/read-body req)))))


(deftest base-request-json
  (let [req (eth/base-request :get "/" :json)]
    (is (= {:remote-addr    "localhost"
            :headers        {"host"         "localhost"
                             "content-type" "application/json"
                             "accept"       "application/json"}
            :server-port    80
            :uri            "/"
            :server-name    "localhost"
            :query-string   nil
            :scheme         :http
            :request-method :get}
           (dissoc req :body)))
    (is (= {}
           (eth/read-body req))))

  (let [req (eth/base-request :post "/" {:x :y} :json)]
    (is (= {:remote-addr    "localhost"
            :headers        {"host"         "localhost"
                             "content-type" "application/json"
                             "accept"       "application/json"}
            :server-port    80
            :uri            "/"
            :server-name    "localhost"
            :query-string   nil
            :scheme         :http
            :request-method :post}
           (dissoc req :body)))
    (is (= {:x "y"}
           (eth/read-body req)))))


(deftest response-contains-one-entity-like-test
  (eth/assert-response-contains-one-entity-like
   [[:entity {:foo {1 {:gurp :lurp
                       :dump :mump}}}]]
   {:gurp :lurp})

  (comment
    ;; don't know of a good way to test test helper failures yet :(
    (eth/assert-response-contains-one-entity-like
     [[:entity {:foo {1 {:gurp :lurp
                         :dump :mump}}}]]
     {:boop :doop})))

(deftest response-contains-entity-like-test
  (eth/assert-response-contains-entity-like
   [[:entity {:bar {1 {:dump :mump}}}]
    [:entity {:foo {1 {:gurp :lurp}}}]]
   {:gurp :lurp})

  (comment
    ;; don't know of a good way to test test helper failures yet :(
    (eth/assert-response-contains-entity-like
     [[:entity {:foo {1 {:gurp :lurp
                         :dump :mump}}}]]
     {:nope :nope})))
