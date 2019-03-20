(ns sweet-tooth.endpoint.datomic.connection
  (:require [com.flyingmachine.datomic-booties.core :as datb]
            [datomic.api :as d]
            [integrant.core :as ig]))

(defmethod ig/init-key :sweet-tooth.endpoint.datomic/connection
  [_ {:keys [uri create? migrate? schema] :as config}]
  (when create? (d/create-database uri))
  (when migrate? (datb/conform (d/connect uri) schema nil nil))
  (assoc config :conn (d/connect (:uri config))))

(defmethod ig/halt-key! :sweet-tooth.endpoint.datomic/connection
  [_ {:keys [delete? uri]}]
  (when delete? (d/delete-database uri)))
