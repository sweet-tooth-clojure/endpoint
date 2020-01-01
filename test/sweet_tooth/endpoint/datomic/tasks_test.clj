(ns sweet-tooth.endpoint.datomic.tasks-test
  (:require [sweet-tooth.endpoint.datomic.tasks :as sut]
            [sweet-tooth.endpoint.datomic.connection]
            [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [datomic.api :as d]))

(def uri "datomic:mem://st")

(defn db-fixture
  [f]
  (d/delete-database uri)
  (f)
  (d/delete-database uri))

(use-fixtures :each db-fixture)

(def db-config
  {:sweet-tooth.endpoint.datomic/connection
   {:uri      uri
    :create?  true
    :migrate? true
    :delete?  true
    :schema   ["schema/user.edn"]}})

(def task-config {:db (ig/ref :sweet-tooth.endpoint.datomic/connection)})

(defn task-system
  [task-name]
  (merge db-config {task-name task-config}))

(deftest test-recreate-db
  (is (thrown? Exception (d/connect uri)))
  (ig/init (task-system ::sut/recreate))
  (is (d/connect uri))
  ;; TODO test that data is deleted or otherwise show this is a new db
  (ig/init (task-system ::sut/recreate))
  (is (d/connect uri)))

(deftest test-install-schemas
  (letfn [(query []
            (d/q '[:find ?e
                   :where [?e :user/username]
                   :in $]
                 (d/db (d/connect uri))))]
    (d/create-database uri)
    (is (thrown? Exception (query)))
    (ig/init (task-system ::sut/install-schemas))
    (is (query))))

(deftest test-delete-db
  (d/create-database uri)
  (is (d/connect uri))
  (ig/init (task-system ::sut/delete-db))
  (is (thrown? Exception (d/connect uri))))
