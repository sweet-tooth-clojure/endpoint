(ns sweet-tooth.endpoint.system
  (:require [integrant.core :as ig]))

;; User
(defmulti config
  "Provides a way for client application to name different duct configs,
  e.g. :test, :dev, :prod, etc"
  identity)

(defn system
  [config-name]
  (ig/init (config config-name)))
