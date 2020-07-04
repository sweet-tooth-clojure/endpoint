(ns sweet-tooth.endpoint.generate
  (:require [rewrite-clj.zip :as rz]
            [rewrite-clj.zip.whitespace :as rzw]
            [rewrite-clj.node :as rn]
            [rewrite-clj.custom-zipper.core :as rcz]
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
   :modify (fn [node form]
             (let [comment-node (-> node
                                    (rz/find-value rz/next 'serr/expand-routes)
                                    rz/right
                                    (rz/find-value rz/next 'comment)
                                    rz/up)
                   comment-left (rz/node (rcz/left comment-node))
                   whitespace   (and (:whitespace comment-left) comment-left)]
               (-> comment-node
                   (rcz/insert-right form)
                   rz/right
                   rzw/insert-newline-left
                   (rcz/insert-left whitespace))))})

(defn update-file!
  [{:keys [modify] :as point} form]
  (let [file-path (path point)]
    (spit file-path (rz/root-string (modify (rz/of-file file-path) form)))))
