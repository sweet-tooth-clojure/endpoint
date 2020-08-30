(ns sweet-tooth.endpoint.datomic.liberator-test
  (:require [clojure.test :refer [deftest is]]
            [com.flyingmachine.datomic-junk :as dj]
            [datomic.api :as d]
            [integrant.core :as ig]
            [sweet-tooth.endpoint.datomic.connection] ; for multimethod
            [sweet-tooth.endpoint.datomic.liberator :as sut]))

(deftest test-ctx-id
  (is (= 1
         (sut/ctx-id {:request {:params {:id "1"}}}))
      (= 17592186056135
         (sut/ctx-id {:request {:params {:id "17592186056135"}}}))))

;;-------
;; interactions with actual datomic db
;;-------

(def db-config
  {:sweet-tooth.endpoint.datomic/connection
   {:uri      "datomic:mem://st"
    :create?  true
    :migrate? true
    :delete?  true
    :schema   ["schema/user.edn"]}})

(defmacro with-system
  [system-name db-name & body]
  `(let [~system-name (ig/init db-config)
         ~db-name     (:sweet-tooth.endpoint.datomic/connection ~system-name)]
     (try ~@body
          (finally (ig/halt! ~system-name)))))

(defn create-user
  [db]
  @(sut/create {:db db
                :request {:params {:user/username "boop"}}}))

;;-----
;; get
;;-----

(deftest pull-ctx-id
  (with-system system db
    (create-user db)
    (let [db-id (-> (d/db (:conn db))
                    (dj/one [:user/username])
                    (:db/id))]
      (is (nil? (sut/pull-ctx-id {:db      db
                                  :id-key  :db/id
                                  :request {:params {:db/id 1000000}}})))
      (is (= {:db/id         db-id
              :user/username "boop"}
             (sut/pull-ctx-id {:db      db
                               :id-key  :db/id
                               :request {:params {:db/id db-id}}}))))))

;;-------
;; create
;;-------

(deftest test-ctx->create-map
  (let [{:keys [title db/id] :as m} (sut/ctx->create-map {:request {:params {:title "boop"
                                                                             :x     nil}}})]
    (is (= "boop" title))
    (is (= :db.part/user (:part id)))
    (is (int? (:idx id)))
    (is (not (contains? m :x)))
    (is (= #{:title :db/id}
           (set (keys m))))))

(deftest test-create
  (with-system system db
    (create-user db)
    (is (= {:user/username "boop"}
           (-> (d/db (:conn db))
               (dj/one [:user/username])
               (select-keys [:user/username]))))))

(deftest test-created-id
  (with-system system db
    (let [new-ctx {:result (create-user db)}]
      (is (= {:user/username "boop"}
             (-> new-ctx
                 sut/created-id
                 (->> (d/entity (d/db (:conn db))))
                 (select-keys [:user/username])))))))

(deftest test-created-entity
  (with-system system db
    (let [new-ctx {:result (create-user db)}]
      (is (= {:user/username "boop"}
             (-> new-ctx
                 sut/created-entity
                 (select-keys [:user/username])))))))

(deftest test-created-pull
  (with-system system db
    (let [new-ctx {:result (create-user db)}
          pulled  (sut/created-pull new-ctx)]
      (is (= "boop" (:user/username pulled)))
      (is (pos-int? (:db/id pulled))))))

;;-------
;; update
;;-------

(defn update-user
  [db params]
  (let [created-id (sut/created-id {:result (create-user db)})]
    (sut/update->:result {:db      db
                          :request {:params (assoc params :id (str created-id))}})))

(deftest test-update->:result
  (with-system system db
    (let [{:keys [result request] :as new-ctx} (update-user db {:user/username "hi"})]
      (is (= db (:db new-ctx)))
      (is (contains? result :db-after))
      (is (contains? result :db-before))
      (is (= "hi" (get-in request [:params :user/username]))))))


(deftest test-ctx->update-map
  (is (= {:title "boop", :db/id 1}
         (sut/ctx->update-map {:id-key  :db/id
                               :request {:params {:db/id 1
                                                  :title "boop"
                                                  :x     nil}}})))

  (is (= {:title "boop", :thing-id 1}
         (sut/ctx->update-map {:id-key  :thing-id
                               :request {:params {:thing-id 1
                                                  :title    "boop"
                                                  :x        nil}}}))))

(deftest test-update
  (with-system system db
    (let [created-id (sut/created-id {:result (create-user db)})]
      (update-user db {:id            (str created-id)
                       :user/username "marple"})
      (is (= {:user/username "marple"}
             (-> (d/db (:conn db))
                 (dj/one [:user/username])
                 (select-keys [:user/username])))))))

(deftest test-updated-entity
  (with-system system db
    (let [new-ctx        (update-user db {:user/username "marple"})
          updated-entity (sut/updated-entity new-ctx)]
      (is (= {:user/username "marple"}
             (into {} updated-entity))))))

(deftest test-updated-pull
  (with-system system db
    (let [new-ctx (update-user db {:user/username "marple"})
          pulled  (sut/updated-pull new-ctx)]
      (is (= {:user/username "marple"} (select-keys pulled [:user/username])))
      (is (= #{:user/username :db/id} (set (keys pulled)))))))

;;-------
;; delete
;;-------

(deftest test-delete
  (with-system system db
    (let [created-id (sut/created-id {:result (create-user db)})
          result     @(sut/delete {:db      db
                                   :request {:params {:id (str created-id)}}})]
      (is (empty? (into {} (d/entity (:db-after result) created-id)))))))
