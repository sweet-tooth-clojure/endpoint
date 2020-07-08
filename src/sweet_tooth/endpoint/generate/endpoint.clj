(ns sweet-tooth.endpoint.generate.endpoint
  "Generator for an endpoint"
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [leiningen.new.templates :as lt]
            [sweet-tooth.generate :as sg]
            [sweet-tooth.endpoint.system :as es]))

(def routes-point
  {:path     ["cross" "endpoint_routes.cljc"]
   :rewrite  (fn [node {:keys [endpoint-ns]}]
               (sg/insert-below-anchor node 'st:begin-ns-routes [(keyword endpoint-ns)]))
   :strategy ::sg/rewrite-file})

(def endpoint-file-point
  ;; TODO this is kinda ugly
  {:path     (fn [{:keys [endpoint-name]}]
               (let [segments (->> (str/split endpoint-name #"\.")
                                   (map lt/sanitize))]
                 (conj (into ["backend" "endpoint"] (butlast segments))
                       (str (last segments) ".clj"))))
   :template "(ns {{endpoint-ns}})

(def decisions
  {:collection
   {:get  {:handle-ok (fn [ctx] [])}
    :post {:handle-created (fn [ctx] [])}}

   :member
   {:get {:handle-ok (fn [ctx] [])}
    :put {:handle-ok (fn [ctx] [])}
    :delete {:handle-ok nil}}})"
   :strategy ::sg/create-file})

(defn generator-opts
  [[endpoint-name {:keys [config-name project-ns] :as opts :or {config-name :dev}}]]
  (let [project-ns (or project-ns (:duct.core/project-ns (es/config config-name)))]
    (merge {:project-ns    project-ns
            :endpoint-name endpoint-name
            :path-base     ["src" (lt/name-to-path project-ns)]
            :endpoint-ns   (->> [project-ns "backend" "endpoint" endpoint-name]
                                (map name)
                                (str/join ".")
                                (symbol))}
           opts)))

(s/fdef generator-opts
  :args (s/cat :args (s/tuple keyword? map?))
  :ret map?)

(def generator
  {:points {:routes        routes-point
            :endpoint-file endpoint-file-point}
   :opts   generator-opts})

(defmethod sg/generator :sweet-tooth/endpoint [_] generator)
