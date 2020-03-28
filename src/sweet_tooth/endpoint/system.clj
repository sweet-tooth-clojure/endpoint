(ns sweet-tooth.endpoint.system
  (:require [integrant.core :as ig]
            [meta-merge.core :as mm]))

;; User
(defmulti config
  "Provides a way for client application to name different duct configs,
  e.g. :test, :dev, :prod, etc"
  identity)

(defn system
  [config-name & [custom-config]]
  (let [cfg (config config-name)]
    (ig/init (cond (not custom-config)  cfg
                   (map? custom-config) (mm/meta-merge cfg custom-config)
                   (fn? custom-config)  (custom-config cfg)))))
