(ns sweet-tooth.endpoint.system
  (:require [integrant.core :as ig]))

(defmulti config identity)

(defn system
  [config-name]
  (ig/init (config config-name)))
