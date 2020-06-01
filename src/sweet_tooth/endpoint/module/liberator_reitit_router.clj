(ns sweet-tooth.endpoint.module.liberator-reitit-router
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

  We derive handler keys from `::handler`. We then define
  `ig/init-key` methods for those keywords, where the method returns a
  handler function.

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
            [clojure.string :as str]
            [clojure.set :as set]
            [duct.logger :as log]))

(defn ns-route?
  "differentiate 'normal' routes from those generated by
  `err/expand-routes`"
  [route]
  (get-in route [1 ::err/ns]))

;;-----------
;; liberator handlers
;;-----------
(defn- resolve-decisions
  "Resolves the var defining liberator decisions for routes"
  [{:keys [decisions ::err/ns]}]
  (try @(ns-resolve (symbol ns) decisions)
       (catch Throwable _t
         (throw (ex-info (format "could not find decision var '%s in %s"
                                 decisions
                                 ns)
                         {:ns        ns
                          :decisions decisions})))))

(defn- log-decision-problems
  "Help the dev as much as possible if they've misconfigured something.

  TODO include a link to an explanation of the warning."
  [logger decision-map decision-ns decision-var-name route-type]
  (let [common-log-data      {:decision-ns       decision-ns
                              :decision-var-name decision-var-name
                              :route-type        route-type}
        valid-methods        #{:get :head :post :put :delete
                               :connect :options :trace :patch}
        unrecognized-methods (set/difference (set (keys decision-map)) valid-methods)]
    (when (empty? decision-map)
      (log/warn logger
                ::no-decisions-defined
                (assoc common-log-data
                       :instructions "Add a decision map for this route-type")))
    (when (not-empty unrecognized-methods)
      (log/warn logger
                ::unrecognized-decision-map-methods
                (assoc common-log-data
                       :unrecognized-methods unrecognized-methods
                       :valid-methods        valid-methods
                       :instructions         "Remove unrecognized methods from the the decision map")))))

(defn liberator-resources
  "Return liberator resource handler for a route type"
  [{:keys [decisions ctx ::err/type ::path] :as endpoint-opts}]
  (let [decision-var (cond (map? decisions)    decisions
                           (symbol? decisions) (resolve-decisions endpoint-opts))
        decision-map (or (get decision-var type)
                         (get decision-var path))]
    (log-decision-problems (:logger ctx)
                           decision-map
                           (::err/ns endpoint-opts)
                           decisions
                           type)
    (->> (el/initialize-decisions decision-map ctx)
         ;; TODO make this configurable?
         (lu/merge-decisions el/decision-defaults)
         (lu/resources {:all (keys el/decision-defaults)})
         :all)))

;; Individual route handlers are derived from these handlers
(defmethod ig/init-key ::handler [_ endpoint-opts]
  ;; a :collection route gets looked up under :collection in handlers,
  ;; likewise with :member or :member/child
  (liberator-resources endpoint-opts))

;;-----------
;; handler component config
;;-----------
(defn- endpoint-handler-key
  "keyword used to define an integrant component for an endpoint
  handler"
  [endpoint-opts]
  (let [ns     (::err/ns endpoint-opts)
        type   (::err/type endpoint-opts)
        prefix (if-let [n (namespace type)]
                 (str n "-" (name type))
                 (name type))]
    (keyword (name ns) (str prefix "-handler"))))

(defn- update-route-opts
  [route f & args]
  (apply update route 1 f args))

(defmacro update-opts-if-ns-route
  [route f & args]
  `(if (ns-route? ~route)
     (update-route-opts ~route ~f ~@args)
     ~route))

(defn- route-opts
  [route]
  (get route 1))

(defn- flip-merge
  [x y]
  (merge y x))

(defn- add-handler-ref
  "Adds an integrant ref to an ns-route for `:handler` so that the
  handler can be initialized by the backend"
  [route]
  (update-opts-if-ns-route route flip-merge {:handler (-> route
                                                          route-opts
                                                          endpoint-handler-key
                                                          ig/ref)}))

(defn- add-ent-type
  [route]
  (update-opts-if-ns-route route flip-merge {:ent-type (-> route
                                                           route-opts
                                                           ::err/ns
                                                           name
                                                           (str/replace #".*\.(?=[^.]+$)" "")
                                                           keyword)}))

(defn- add-path
  [route]
  (update-opts-if-ns-route route flip-merge {::path (first route)}))

(defn- add-id-keys
  [route]
  (update-opts-if-ns-route route flip-merge {:id-key :id, :auth-id-key :id}))

(defn- format-middleware-fn
  "Associates opts which are later used by the format middleware to
  format responses."
  [[_ endpoint-opts]]
  (fn format-middleware [f]
    (fn [req]
      (assoc (f req) :sweet-tooth.endpoint/format (select-keys endpoint-opts [:id-key :ent-type])))))

(defn- add-middleware
  "Middleware is added to reitit in order to work on the request map
  that reitit produces before that request map is passed to the
  handler. For example, reitit adds route params to the request map."
  [route]
  (update-route-opts route update :middleware #(mm/meta-merge [(format-middleware-fn route)
                                                               em/wrap-merge-params]
                                                              %)))

(defn- add-decisions
  "Add default `'decisions` symbol, meaning that var will get resolved
  to retrieve the decisions"
  [route]
  (update-opts-if-ns-route route flip-merge {:decisions 'decisions}))

(defn- add-default-ctx
  [[_ endpoint-opts :as route]]
  (let [ctx (merge (select-keys endpoint-opts [:id-key :auth-id-key])
                   {:logger                         (ig/ref :duct/logger)
                    :sweet-tooth.endpoint/namespace (::err/ns endpoint-opts)})]
    (update-opts-if-ns-route route update :ctx flip-merge ctx)))

(defn add-route-defaults
  "Compose the final route passed to reitit/router"
  [route]
  (-> route
      add-id-keys
      add-ent-type
      add-handler-ref
      add-middleware))

(defn add-handler-defaults
  "Compose configuration used for handler components, used to create
  liberator handlers"
  [route]
  (-> route
      add-id-keys
      add-ent-type
      add-path
      add-default-ctx
      add-decisions))

(defn add-route-handler-to-config
  [config [_ route-opts]]
  (assoc config (endpoint-handler-key route-opts) route-opts))

;;-----------
;; create the router
;;-----------

(defn- resolve-ns-routes
  "User can specify routes directly for the ::routes module, or
  use a symbol or keyword that will get resolved to the corresponding
  var."
  [routes]
  (cond (vector? routes) routes

        (or (keyword? routes)
            (symbol? routes))
        (try (require (symbol (namespace routes)))
             @(ns-resolve (symbol (namespace routes))
                          (symbol (name routes)))
             (catch Exception _e
               (throw (ex-info (format "Your duct configuration for %s is incorrect. Could not find the var specified by :routes."
                                       :sweet-tooth.endpoint.module/liberator-reitit-router)
                               {:routes routes}))))))

;; This module populates the system config with a ::router component
;; and with components for each individual handler needed for the
;; routes
(defmethod ig/init-key :sweet-tooth.endpoint.module/liberator-reitit-router
  [_ {:keys [routes]}]
  (let [routes (resolve-ns-routes routes)]
    (fn [config]
      ;; This lets us use the `::handler` ig/init-key method for all
      ;; handlers.
      (doseq [endpoint-opts (->> routes
                                 (filter ns-route?)
                                 (map #(get % 1)))]
        (derive (endpoint-handler-key endpoint-opts) ::handler))

      (-> config
          (duct/merge-configs
            {::reitit-router (mapv add-route-defaults routes)}
            (->> routes
                 (filter ns-route?)
                 (mapv add-handler-defaults)
                 (reduce add-route-handler-to-config {})))
          (dissoc :duct.router/cascading)))))

;; This is the actual component that the duct web module picks up and
;; uses as the router for the ring stack.
(defmethod ig/init-key ::reitit-router [_ routes]
  (rr/ring-handler (rr/router routes)))
