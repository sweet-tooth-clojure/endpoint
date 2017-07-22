(ns sweet-tooth.endpoint.group
  "Add multiple endpoints to cascding routes, and give them all the same options"
  (:require [integrant.core :as ig]
            [duct.core :as duct]))

(derive :sweet-tooth.endpoint/group :duct/module)

(defmethod ig/init-key :sweet-tooth.endpoint/group [_ groups]
  {:req #{:duct.router/cascading}
   :fn (fn [config]
         (duct/merge-configs
           config
           (reduce-kv (fn [c endpoints common-opts]
                        (reduce (fn [c e] (assoc c e common-opts))
                                c
                                endpoints))
                      {:duct.router/cascading (->> groups keys (mapcat #(map ig/ref %)) vec)}
                      groups)))})
