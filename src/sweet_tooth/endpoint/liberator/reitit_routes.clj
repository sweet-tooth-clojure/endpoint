(ns sweet-tooth.endpoint.liberator.reitit-routes
  "Module for creating a ring router (a function that receives a ring
  requests and dispatches to a handler function based on the request's
  URL). It ties together the sugar for defining reitit routes and
  liberator handlers.

  The module uses routes produced by
  `sweet-tooth.endpoint.routes.reitit/expand-routes`. These routes
  contain metadata that allow the module to look up vars that define
  liberator handlers. The module is responsible for associating routes
  with handlers.

  The module is also responsible for adding each handler's
  configuration to the system's integrant configuration, reducing
  boilerplate for the developer.

  We derive handler keys from `::unary-handler` or
  `::coll-handler`. We then define `ig/init-key` methods for those
  keywords, where the method returns a handler function.

  This module serves Sweet Tooth's goal of reducing the boilerplate
  required to get a system running, reducing the potential for errors
  and allowing the developer to focus on what's unique to their
  application."
  (:require [duct.core :as duct]
            [integrant.core :as ig]
            [reitit.ring :as rr]
            [com.flyingmachine.liberator-unbound :as lu]
            [meta-merge.core :as mm]

            [sweet-tooth.endpoint.liberator :as el]
            [sweet-tooth.endpoint.middleware :as em]
            [sweet-tooth.endpoint.routes.reitit :as err]
            [clojure.string :as str]))

(defn ns-route?
  "differentiate 'normal' routes from those generated by
  `err/expand-routes`"
  [route]
  (get-in route [1 ::err/ns]))

;;-----------
;; duct
;;-----------
(defn liberator-resources
  "Return both unary and collection request handlers"
  [endpoint-opts]
  (let [endpoint-ns (::err/ns endpoint-opts)
        decisions   (try (-> (ns-resolve (symbol endpoint-ns) 'decisions)
                             deref
                             (el/initialize-decisions (assoc (:ctx endpoint-opts)
                                                             :sweet-tooth.endpoint/namspace endpoint-ns)))
                         (catch Throwable t (throw (ex-info "could not find 'decisions in namespace" {:ns (::err/ns endpoint-opts)}))))]
    (->> decisions
         (lu/merge-decisions el/decision-defaults)
         (lu/resources lu/resource-groups))))

;; Individual route handlers are derived from these handlers
(defmethod ig/init-key ::unary-handler [_ endpoint-opts]
  (:entry (liberator-resources endpoint-opts)))
(defmethod ig/init-key ::coll-handler [_ endpoint-opts]
  (:collection (liberator-resources endpoint-opts)))

(defn endpoint-handler-key
  [endpoint-opts]
  (let [ns   (::err/ns endpoint-opts)
        type (::err/type endpoint-opts)]
    (keyword (name ns) (case type
                         ::err/unary "unary-handler"
                         ::err/coll  "coll-handler"))))

(defn add-handler-ref
  "Adds an integrant ref to an ns-route for `:handler` so that the
  handler can be initialized by the backend"
  [ns-route]
  (let [{:keys [sweet-tooth.endpoint.routes.reitit/ns handler]} (get ns-route 1)]
    (cond-> ns-route
      (and (not handler) ns) (assoc-in [1 :handler] (-> ns-route
                                                        (get 1)
                                                        endpoint-handler-key
                                                        ig/ref)))))

(defn add-ent-type
  [[_ endpoint-opts :as route]]
  (if (ns-route? route)
    (update-in route [1 :ent-type] #(or % (-> endpoint-opts
                                              ::err/ns
                                              name
                                              (str/replace #".*\.(?=[^.]+$)" "")
                                              keyword)))
    route))

(defn add-id-keys
  [ns-route]
  (if (ns-route? ns-route)
    (let [[_ {:keys [id-key auth-id-key]
              :or   {id-key      :id
                     auth-id-key :id}
              :as   route-data}] ns-route
          id-keys                {:id-key      id-key
                                  :auth-id-key auth-id-key}]
      (assoc ns-route 1 (-> route-data
                            (merge id-keys)
                            (update :ctx (fn [ctx] (merge id-keys ctx))))))
    ns-route))

(defn format-middleware-fn
  [[_ endpoint-opts]]
  (fn format-middleware [f]
    (fn [req]
      (assoc (f req)
             :sweet-tooth.endpoint/format (select-keys endpoint-opts [:id-key :auth-id-key :ent-type])))))

(defn add-middleware
  "Middleware is added to reitit in order to work on the request map
  that reitit produces before that request map is passed to the
  handler"
  [ns-route]
  (update-in ns-route
             [1 :middleware]
             #(mm/meta-merge [(format-middleware-fn ns-route)
                              em/wrap-merge-params]
                             %)))

(defn add-route-defaults
  ""
  [route-config]
  (-> route-config
      add-id-keys
      add-ent-type
      add-handler-ref
      add-middleware))

(defn add-handler-defaults
  [route-config]
  (-> route-config
      add-id-keys
      add-ent-type))

(defn add-ns-route-config
  [ns-route-config [_ ns-route-opts]]
  (cond-> ns-route-config
    (::err/ns ns-route-opts) (assoc (endpoint-handler-key ns-route-opts) ns-route-opts)))

(defn- resolve-ns-routes
  "User can specify ns-routes directly for the ::ns-routes module, or
  use a symbol or keyword that will get resolved to the corresponding
  var."
  [ns-routes]
  (cond (vector? ns-routes) ns-routes

        (or (keyword? ns-routes)
            (symbol? ns-routes))
        (try (require (symbol (namespace ns-routes)))
             @(ns-resolve (symbol (namespace ns-routes))
                          (symbol (name ns-routes)))
             (catch Exception e
               (throw (ex-info "Your duct configuration for :sweet-tooth.endpoint.liberator.reitit-routes/ns-routes is incorrect. Could not find the var specified by :ns-routes."
                        {:ns-routes ns-routes}))))))

;; This is a module
(defmethod ig/init-key ::ns-routes [_ {:keys [ns-routes]}]
  (let [ns-routes (resolve-ns-routes ns-routes)]
    (fn [config]
      ;; Have each endpoint handler's integrant key drive from a
      ;; default key
      (doseq [endpoint-opts (->> ns-routes
                                 (filter ns-route?)
                                 (map #(get % 1)))]
        (derive (endpoint-handler-key endpoint-opts)
                (case (::err/type endpoint-opts)
                  ::err/unary ::unary-handler
                  ::err/coll  ::coll-handler)))

      (-> config
          (duct/merge-configs
            {::router (mapv add-route-defaults ns-routes)}
            (->> ns-routes
                 (mapv add-handler-defaults)
                 (filter ns-route?)
                 (reduce add-ns-route-config {})))
          (dissoc :duct.router/cascading)))))

;; This is a component
(defmethod ig/init-key ::router [_ routes]
  (rr/ring-handler (rr/router routes)))
