(ns sweet-tooth.endpoint.group-routes
  "Add multiple endpoints to cascading routes and give them all the same
  options"
  (:require [integrant.core :as ig]
            [duct.core :as duct]))

(derive :sweet-tooth.endpoint/group-routes :duct/module)

;; hide the config in a delay so that ig/refs aren't resolved at
;; module fold time
(defmethod ig/prep-key :sweet-tooth.endpoint/group-routes [_ groups]
  (delay groups))

(defmethod ig/init-key :sweet-tooth.endpoint/group-routes [_ groups]
  (fn [config]
    (duct/merge-configs
      config
      (reduce-kv (fn [c endpoints common-opts]
                   (reduce (fn [c e] (assoc c e common-opts))
                           c
                           endpoints))
                 {:duct.router/cascading (->> @groups keys (mapcat #(map ig/ref %)) vec)}
                 @groups))))
