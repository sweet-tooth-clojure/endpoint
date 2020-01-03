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

(defn query []
  (d/q '[:find ?e
         :where [?e :user/username]
         :in $]
       (d/db (d/connect uri))))

(defn exec-task
  [task-name]
  (let [system (ig/init (merge db-config {task-name task-config}))]
    ((task-name system))))

(deftest test-recreate-db
  (is (thrown? Exception (d/connect uri)))
  (exec-task ::sut/recreate)
  (d/transact (d/connect uri) [{:db/id (d/tempid :db.part/user) :user/username "boop"}])
  (is (not-empty (query)))
  (exec-task ::sut/recreate)
  (is (empty? (query))))

(deftest test-install-schemas
  (d/create-database uri)
  (is (thrown? Exception (query)))
  (exec-task ::sut/install-schemas)
  (is (query)))

(deftest test-delete-db
  (d/create-database uri)
  (is (d/connect uri))
  (exec-task ::sut/delete-db)
  (is (thrown? Exception (d/connect uri))))
