(ns sweet-tooth.endpoint.routes.reitit
  (:require [clojure.string :as str]
            [integrant.core :as ig]
            [duct.core :as duct]
            [reitit.ring :as rr]))

(defn- include-route?
  [opts k]
  (or (not (contains? opts k))
      (k opts)))

(defn- slash
  [endpoint-name]
  (str/replace endpoint-name #"\." "/"))

(defn coll-route
  [endpoint-name ns opts]
  (let [coll-opts    (::coll opts)
        {:keys [path-prefix]
         :or   {path-prefix ""}
         :as   opts} (dissoc opts ::coll ::unary)]
    [(format "%s/%s" path-prefix (slash endpoint-name))
     (merge {:name  (keyword (str endpoint-name "s"))
             ::ns   ns
             ::type ::coll}
            opts
            coll-opts)]))

(defn unary-route
  [endpoint-name ns opts]
  (let [unary-opts   (::unary opts)
        {:keys [path-prefix]
         :or   {path-prefix ""}
         :as   opts} (dissoc opts ::coll ::unary)
        id-key       (or (:id-key unary-opts) (:id-key opts) :id)]
    [(format "%s/%s/{%s}" path-prefix (slash endpoint-name) (subs (str id-key) 1))
     (merge {:name  (keyword endpoint-name)
             ::ns   ns
             ::type ::unary}
            opts
            unary-opts)]))

(defn ns-pair->ns-route
  [[ns opts]]
  (let [endpoint-name (str/replace (name ns) #".*?endpoint\." "")
        path          (str "/" endpoint-name)]
    (cond-> []
      (include-route? opts ::coll)  (conj (coll-route endpoint-name ns opts))
      (include-route? opts ::unary) (conj (unary-route endpoint-name ns opts)))))

(defn merge-common
  "Merge common route opts such that when the val is a map, it's merged
  rather than replaced"
  [route-opts common-opts]
  (merge-with #(if (map? %2) (merge %1 %2) (or %2 %1))
              common-opts
              route-opts))

(defn ns-pairs->ns-routes
  "Returns vector of reitit-compatible routes from compact route syntax"
  [pairs]
  (loop [common             {}
         [pair & remaining] pairs
         routes             []]
    (cond (map? pair) (recur pair remaining routes)
          (not pair)  routes
          :else       (recur common
                             remaining
                             (into routes (ns-pair->ns-route (update pair 1 merge-common common)))))))

;; common endpoint route setup
;; TODO
;; - look up decisions
;; - add initial context to decisions
;; - turn decisions into a single handler
(defmethod ig/init-key ::ns-route [k endpoint-opts]
  ;; TODO create the liberator handler from decisions
  ())

(defn ns-route-key
  [ns]
  (keyword ns :handler))

;; hide the config in a delay so that ig/refs aren't resolved at
;; module fold time
(derive ::ns-routes :duct/module)

;; ns-pairs is a value returned by `ns-routes`
(defmethod ig/init-key ::ns-routes [_ ns-routes]
  (fn [config]
    (doseq [k (ns-route-key)]
      (derive k ::ns-route))
    (duct/merge-configs
      config
      {:duct.router/cascading [(ig/ref ::ns-router)]
       ::ns-router            (mapv (fn [route] (update-in route [1 :handler] #(or % (-> route
                                                                                         ::ns
                                                                                         ns-route-key
                                                                                         ig/ref))))
                                    ns-routes)}
      (reduce (fn [ns-route-config [_ ns-route-opts]]
                (assoc ns-route-config (ns-route-key (::ns ns-route-opts)) ns-route-opts))
              {}
              ns-routes))))


(comment
  {::endpoint-routes 'the-routes/routes} ; =>

  {:duct.router/cascading [(ig/ref ::ns-router)]
   
   ::ns-router ["/x" {::ns     :x.endpoint.topic
                      :handler (ig/ref :x.endpoint.topic/handler)}]

   :x.endpoint.topic/handler {::ns     :x.endpoint.topic
                              :handler (ig/ref :x.endpoint.topic/handler)}})


