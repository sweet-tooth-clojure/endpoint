(ns sweet-tooth.endpoint.generate
  (:require [rewrite-clj.zip :as rz]
            [rewrite-clj.zip.whitespace :as rzw]
            [sweet-tooth.endpoint.system :as es]
            [clojure.string :as str]))

(defn- src-base* [config]
  ["src" (name (:duct.core/project-ns config))])

(defn  src-base [config-name]
  (src-base* (es/config config-name)))

(defn path
  ([point]
   (path point :dev))
  ([point config-name]
   (str/join "/" (into (src-base config-name) (:path point)))))


(def endpoint-routes-point
  {:path ["cross" "endpoint_routes.cljc"]
   :modify (fn [node ns-kw]
             (-> node
                 (rz/find-value rz/next 'serr/expand-routes)
                 rz/right
                 (rz/find-value rz/next 'comment)
                 rz/up
                 (rz/insert-right [ns-kw])
                 rz/right
                 rzw/insert-newline-left
                 rzw/insert-space-left
                 rzw/insert-space-left
                 rzw/insert-space-left
                 rzw/insert-space-left
                 rz/print-root))})
