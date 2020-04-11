(ns sweet-tooth.endpoint.module.mock
  "When a component's config value is :sweet-tooth.endpoint.mock/mock-component

  "
  (:require [integrant.core :as ig]
            [sweet-tooth.endpoint.mock :as mock]
            [clojure.spec.alpha :as s]))

(s/def ::st-mock-keypair (s/tuple keyword? keyword?))

(defn- mock-key
  [k]
  (-> k
      (str "-mock")
      (subs 1)
      (keyword)))

(defn derive-mock-keys
  [st-mock-keypairs]
  (doseq [mocked-key (disj (reduce into #{} st-mock-keypairs) :st/mock)]
    (let [mk (mock-key mocked-key)]
      (derive mk mocked-key)
      (derive mk ::mock/mock-component))))

(defmethod ig/init-key :sweet-tooth.endpoint.module/mock [_ _]
  (fn [config]
    (prn "CONFIG" config)
    (let [st-mock-keypairs (->> (ig/find-derived config :st/mock)
                                (map first))]
      (derive-mock-keys st-mock-keypairs)
      (reduce (fn [config st-mock-keypair]
                (let [mocked-key (first (filter #(not= :st/mock %) st-mock-keypair))
                      config-val (get config st-mock-keypair)]
                  (-> config
                      (dissoc mocked-key st-mock-keypair)
                      (assoc (mock-key mocked-key) config-val))))
              config
              st-mock-keypairs))))
