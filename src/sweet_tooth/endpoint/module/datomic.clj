(ns sweet-tooth.endpoint.module.datomic
  (:require [integrant.core :as ig]
            [duct.core :as duct]
            [sweet-tooth.endpoint.datomic.tasks :as dt]))

(defmethod ig/init-key ::tasks [_ task-config-override]
  (fn [config]
    (let [task-config (merge {:db     (ig/ref :sweet-tooth.endpoint.datomic/connection)
                              :logger (ig/ref :duct/logger)}
                             task-config-override)]
      (duct/merge-configs {::dt/recreate        task-config
                           ::dt/install-schemas task-config
                           ::dt/delete-db       task-config}
                          config))))
