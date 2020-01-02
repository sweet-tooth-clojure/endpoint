(ns sweet-tooth.endpoint.datomic.tasks
  (:require [com.flyingmachine.datomic-booties.core :as datb]
            [integrant.core :as ig]
            [datomic.api :as d]
            [duct.logger :as log]))

(defmethod ig/init-key ::recreate [_ {{:keys [uri schema data] :as db} :db
                                      :keys [logger]}]
  (fn []
    (when logger (log/info logger ::recreate-started {:uri uri}))
    (d/delete-database uri)
    (d/create-database uri)
    (datb/conform (d/connect uri) schema data identity)
    (when logger (log/info logger ::recreate-finished))))

(defmethod ig/init-key ::install-schemas [_ {{:keys [uri schema data]} :db
                                             :keys [logger]}]
  (fn []
    (when logger (log/info logger ::install-schemas-started {:uri uri}))
    (d/create-database uri)
    (datb/conform (d/connect uri) schema data identity)
    (when logger (log/info logger ::install-schemas-finished))))

(defmethod ig/init-key ::delete-db [_ {{:keys [uri]} :db
                                       :keys [logger]}]
  (fn []
    (when logger (log/info logger ::delete-db-started {:uri uri}))
    (d/delete-database uri)
    (when logger (log/info logger ::delete-db-finished))))
