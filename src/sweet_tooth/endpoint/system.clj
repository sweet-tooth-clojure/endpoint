(ns sweet-tooth.endpoint.system
  (:require [integrant.core :as ig]
            [meta-merge.core :as mm]))

(defmulti config
  "Provides a way for client application to name different integrant configs,
  e.g. :test, :dev, :prod, etc"
  identity)

(defn init-key
  "If a component config is tagged with `^:component` metadata, then use
  the config value instead of initializing it with `ig/init-key`"
  [k v]
  (if (:component (meta v))
    v
    (ig/init-key k v)))

(defn init
  "Like integrant.core/init but, allows config values to get tagged with
  `^:component` metadata. This makes it much easier to use an
  alternative implementation for a component, for instance when
  mocking them."
  ([config]
   (init config (keys config)))
  ([config keys]
   {:pre [(map? config)]}
   (ig/build config keys init-key #'ig/assert-pre-init-spec ig/resolve-key)))

(defn system
  [config-name & [custom-config]]
  (let [cfg (config config-name)]
    (init (cond (not custom-config)  cfg
                (map? custom-config) (mm/meta-merge cfg custom-config)
                (fn? custom-config)  (custom-config cfg)))))
