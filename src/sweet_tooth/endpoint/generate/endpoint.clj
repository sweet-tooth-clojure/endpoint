(ns sweet-tooth.endpoint.generate.endpoint
  "Generator points for an endpoint"
  (:require [rewrite-clj.custom-zipper.core :as rcz]
            [rewrite-clj.zip :as rz]
            [rewrite-clj.zip.whitespace :as rzw]
            [clojure.string :as str]))

(def routes-point
  {:path     ["cross" "endpoint_routes.cljc"]
   :modify   (fn [node form]
               (let [comment-node (-> node
                                      (rz/find-value rz/next 'serr/expand-routes)
                                      rz/right
                                      (rz/find-value rz/next 'st:begin-api-routes)
                                      rz/up)
                     comment-left (rz/node (rcz/left comment-node))
                     whitespace   (and (:whitespace comment-left) comment-left)]
                 (-> comment-node
                     (rcz/insert-right form)
                     rz/right
                     rzw/insert-newline-left
                     (rcz/insert-left whitespace))))
   :form     (fn [{:keys [endpoint-ns]}]
               [(keyword endpoint-ns)])
   :strategy :sweet-tooth.endpoint.generate/modify-file})

;; TODO handle nested endpoints?
(def endpoint-file-point
  {:path     (fn [{:keys [endpoint-name]}]
               ["backend" "endpoint" (str endpoint-name ".clj")])
   :template "(ns {{project-ns}}.backend.endpoint.{{endpoint-name}})

(def decisions
  {:collection
   {}

   :member
   {}})"
   :strategy :sweet-tooth.endpoint.generate/create-file})

(def package
  {:points         {:routes        routes-point
                    :endpoint-file endpoint-file-point}
   :default-points #{:routes :endpoint-file}
   :opts           (fn [{:keys [project-ns endpoint-name]}]
                     {:endpoint-ns (->> [project-ns "backend" "endpoint" endpoint-name]
                                        (map name)
                                        (str/join ".")
                                        (symbol))})
   :name           :sweet-tooth/endpoint})
