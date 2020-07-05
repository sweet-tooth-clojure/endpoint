(ns sweet-tooth.endpoint.generate.endpoint
  "Generator points for an endpoint"
  (:require [rewrite-clj.custom-zipper.core :as rcz]
            [rewrite-clj.zip :as rz]
            [rewrite-clj.zip.whitespace :as rzw]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [sweet-tooth.endpoint.generate :as sg]
            [sweet-tooth.endpoint.system :as es]))

(def routes-point
  {:path     ["cross" "endpoint_routes.cljc"]
   :rewrite  (fn [node {:keys [endpoint-ns]}]
               (let [form         [(keyword endpoint-ns)]
                     comment-node (-> node
                                      (rz/find-value rz/next 'serr/expand-routes)
                                      rz/right
                                      (rz/find-value rz/next 'st:begin-ns-routes)
                                      rz/up)
                     comment-left (rz/node (rcz/left comment-node))
                     whitespace   (and (:whitespace comment-left) comment-left)]
                 (-> comment-node
                     (rcz/insert-right form)
                     rz/right
                     rzw/insert-newline-left
                     (rcz/insert-left whitespace))))
   :strategy :sweet-tooth.endpoint.generate/rewrite-file})

;; TODO handle nested endpoints?
(def endpoint-file-point
  {:path     (fn [{:keys [endpoint-name]}]
               ["backend" "endpoint" (str endpoint-name ".clj")])
   :template "(ns {{endpoint-ns}})

(def decisions
  {:collection
   {:get  {:handle-ok (fn [ctx] [])}
    :post {:handle-created (fn [ctx] [])}}

   :member
   {:get {:handle-ok (fn [ctx] [])}
    :put {:handle-ok (fn [ctx] [])}
    :delete {:handle-ok nil}}})"
   :strategy :sweet-tooth.endpoint.generate/create-file})

(defn package-opts
  [[endpoint-name {:keys [config-name project-ns] :as opts :or {config-name :dev}}]]
  (let [project-ns (or project-ns (:duct.core/project-ns (es/config config-name)))]
    (merge {:project-ns    project-ns
            :endpoint-name endpoint-name
            :path-base     ["src" project-ns]
            :endpoint-ns   (->> [project-ns "backend" "endpoint" endpoint-name]
                                (map name)
                                (str/join ".")
                                (symbol))}
           opts)))

(s/fdef package-opts
  :args (s/cat :args (s/tuple keyword? map?))
  :ret map?)

(def package
  {:points         {:routes        routes-point
                    :endpoint-file endpoint-file-point}
   :opts           package-opts})

(defmethod sg/package :sweet-tooth/endpoint [_] package)
