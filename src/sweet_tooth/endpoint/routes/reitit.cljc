(ns sweet-tooth.endpoint.routes.reitit
  "Sugar for reitit routes. Lets you:

  1. Specify a map of options that apply to a group of routes
  2. Transform names (usually namespace names) into reitit
  routes that include both:
     2a. a collection routes, e.g. `/users`
     2b. a unary route, e.g. `/user/{id}`

  A sugared route definition might be:

  [[:my-app.endpoint.user]]

  This would expand to:

  [[\"/user\" {:name        :users
               ::sterr/ns   :my-app.endpoint.user
               ::sterr/type ::sterr/coll
               :id-key      :id}]
  [\"/user/{id}\" {:name        :user
                   ::sterr/ns   :my-app.endpoint.user
                   ::sterr/type ::sterr/unary
                   :id-key      :id}]]"
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [meta-merge.core :as mm]
            #?@(:cljs [[goog.string :as gstr]
                       [goog.string.format]]))
  (:refer-clojure :exclude [name]))

(s/def ::name keyword?)
(s/def ::route-opts map?)
(s/def ::name-route (s/cat :name ::name
                           :route-opts  (s/? ::route-opts)))

(def format-str  #?(:clj format :cljs gstr/format))

(defn ksubs
  [k]
  (if (keyword? k)
    (-> k str (subs 1))
    k))

(defn slash
  "replace dots with slashes in domain name to create a string that's
  route-friendly"
  [name]
  (str/replace (ksubs name) #"\." "/"))

(defn path
  [{:keys [full-path path path-prefix path-suffix] :as opts}]
  (or full-path
      (->> [path-prefix path path-suffix]
           (map (fn [s] (if (fn? s) (s opts) s)))
           (remove empty?)
           (str/join ""))))

(defn- dissoc-opts
  [opts]
  (dissoc opts :coll :ent ::base-name
          :full-path :path :path-prefix :path-suffix
          :route-types))

(defn route-opts
  [nsk type defaults opts]
  (let [ro (merge {::ns   nsk
                   ::type type}
                  defaults
                  opts
                  (type opts))]
    [(path ro) (dissoc-opts ro)]))

(defmulti expand-route-type (fn [_nsk route-type _opts] route-type))

(defmethod expand-route-type
  :coll
  [nsk route-type {:keys [::base-name] :as opts}]
  (route-opts nsk
              route-type
              {:name (keyword (str base-name "s"))
               :path (str "/" (slash base-name))}
              opts))

(defmethod expand-route-type
  :ent
  [nsk route-type opts]
  (route-opts nsk
              route-type
              {:name   (keyword (::base-name opts))
               :id-key :id
               :path   (fn [{:keys [id-key] :as o}]
                         (format-str "/%s/{%s}"
                                     (slash (::base-name o))
                                     (ksubs id-key)))}
              opts))

(defmethod expand-route-type
  :singleton
  [nsk route-type {:keys [::base-name] :as opts}]
  (route-opts nsk
              route-type
              {:name  (keyword base-name)
               :path  (str "/" (slash base-name))}
              opts))

;; By default unrecognized keys are treated as
;; ["/ent-type/{id}/unrecognized-key" {:name :ent-type.unrecognized-key}]
(defmethod expand-route-type
  :default
  [nsk route-type {:keys [::base-name] :as opts}]
  (route-opts nsk
              route-type
              {:name   (keyword (str base-name "." (ksubs route-type)))
               :id-key :id
               :path   (fn [{:keys [id-key] :as o}]
                         (format-str "/%s/{%s}/%s"
                                     (slash (::base-name o))
                                     (ksubs id-key)
                                     (ksubs route-type)))}
              opts))

(defn expand-route
  "In a pair of [n m], if n is a keyword then the pair is treated as a
  name route and is expanded. Otherwise the pair returned as-is (it's
  probably a regular reitit route).

  `delimiter` is a regex used to specify what part of the name to
  ignore. By convention Sweet Tooth expects you to use names like
  `:my-app.backend.endpoint.user`, but you want to just use `user` -
  that's what the delimiter is for."
  ([pair] (expand-route pair #"endpoint\."))
  ([[ns opts :as pair] delimiter]
   (if (s/valid? ::name-route pair)
     (let [base-name (-> (str ns)
                         (str/split delimiter)
                         (second))
           types     (:route-types opts [:coll :ent])
           opts      (assoc opts ::base-name base-name)]
       (reduce (fn [routes type]
                 (conj routes (expand-route-type ns type opts)))
               []
               types))
     [pair])))

(defn expand-routes
  "Returns vector of reitit-compatible routes from compact route syntax"
  ([pairs]
   (expand-routes pairs #"endpoint\."))
  ([pairs delimiter]
   (loop [common                {}
          [current & remaining] pairs
          routes                []]
     (cond (not current)  routes
           (map? current) (recur current remaining routes)
           :else          (recur common
                                 remaining
                                 (into routes (expand-route (update current 1 #(mm/meta-merge common %))
                                                            delimiter)))))))
