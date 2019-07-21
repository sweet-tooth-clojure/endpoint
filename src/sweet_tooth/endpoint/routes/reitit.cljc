(ns sweet-tooth.endpoint.routes.reitit
  (:require [clojure.string :as str]
            [integrant.core :as ig]
            [duct.core :as duct]
            [reitit.ring :as rr]
            [sweet-tooth.endpoint.liberator :as el]
            #?(:clj [com.flyingmachine.liberator-unbound :as lu])))

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

;;-----------
;; duct
;;-----------

(defmethod ig/init-key ::ns-route-handler [k endpoint-opts]
  #?(:clj
     (let [decisions (-> (ns-resolve (symbol (::ns endpoint-opts)) 'decisions)
                         deref
                         (el/initialize-decisions (dissoc endpoint-opts ::type)))
           resources (->> decisions
                          (lu/merge-decisions el/decision-defaults)
                          (lu/resources lu/resource-groups))]
       (if (= ::type ::coll)
         (:collection resources)
         (:entry resources)))))

(defn ns-route-handler-key
  [ns]
  (keyword (name ns) "route-handler"))

(defn add-handler-ref
  "Adds an integrant ref to an ns-route for `:handler` so that the
  handler can be initialized by the backend"
  [ns-route]
  (update-in ns-route [1 :handler] #(or % (-> ns-route
                                              (get-in [1 ::ns])
                                              ns-route-handler-key
                                              ig/ref))))

(defmethod ig/init-key ::x [_ opts]
  (fn [config]
    (duct/merge-configs config {:x :y})))

(derive ::ns-routes :duct/module)
(defmethod ig/init-key ::ns-routes [_ {:keys [ns-routes]}]
  #?(:clj
     (let [ns-routes (cond (vector? ns-routes) ns-routes
                           (or (keyword? ns-routes)
                               (symbol? ns-routes))
                           (do (require (symbol (namespace ns-routes)))
                               @(ns-resolve (symbol (namespace ns-routes))
                                            (symbol (name ns-routes)))))]
       (fn [config]
         (doseq [k (map #(get-in % [1 ::ns]) ns-routes)]
           (derive (ns-route-handler-key k) ::ns-route-handler))
         (duct/merge-configs
           config
           {:duct.router/cascading [(ig/ref ::ns-router)]
            ::ns-router            (mapv add-handler-ref ns-routes)}
           (reduce (fn [ns-route-config [_ ns-route-opts]]
                     (assoc ns-route-config (ns-route-handler-key (::ns ns-route-opts)) ns-route-opts))
                   {}
                   ns-routes))))))

(defmethod ig/init-key ::ns-router [_ routes]
  (rr/router routes))

(comment
  {::endpoint-routes 'the-routes/routes} ; =>

  {:duct.router/cascading [(ig/ref ::ns-router)]
   
   ::ns-router ["/x" {::ns     :x.endpoint.topic
                      :handler (ig/ref :x.endpoint.topic/handler)}]

   :x.endpoint.topic/handler {::ns     :x.endpoint.topic
                              :handler (ig/ref :x.endpoint.topic/handler)}})


