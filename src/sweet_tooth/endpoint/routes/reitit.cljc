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

(def ^:private format-str  #?(:clj format :cljs gstr/format))

(defn- include-route?
  "all route types are included unless otherwise specified"
  [pair-opts route-type]
  (or (not (contains? pair-opts route-type))
      (route-type pair-opts)))

(defn- ksubs
  [k]
  (if (keyword? k)
    (-> k str (subs 1))
    k))

(defn- slash
  "replace dots with slashes in domain name to create a string that's
  route-friendly"
  [name]
  (str/replace (ksubs name) #"\." "/"))

(defn- path
  [{:keys [full-path path path-prefix path-suffix]}]
  (or full-path
      (->> [path-prefix path path-suffix]
           (remove empty?)
           (str/join ""))))

(defn- dissoc-opts
  [opts]
  (dissoc opts ::coll ::unary :full-path :path :path-prefix :path-suffix))

(defn- build-opts
  [name ns opts type]
  (merge {:name  (keyword (str name (if (= ::coll type) "s")))
          ::ns   ns
          ::type type}
         opts
         (type opts)))

(defn coll-route
  [name ns opts]
  (let [final-opts (build-opts name ns opts ::coll)]
    [(path (update final-opts :path #(or % (str "/" (slash name)))))
     (dissoc-opts final-opts)]))

(defn unary-route
  [name ns opts]
  (let [final-opts (build-opts name ns opts ::unary)
        id-key     (:id-key final-opts :id)]
    [(path (update final-opts :path
                   #(or % (format-str "/%s/{%s}" (slash name) (ksubs id-key)))))
     (dissoc-opts final-opts)]))

(defn transform-singleton
  "Handles cases where the route corresponds to a 'singleton' resource,
  like a user session"
  [single-name opts]
  (if (::singleton? opts)
    (-> opts
        (assoc ::unary false)
        (update-in [::coll :name] #(or % (keyword single-name))))
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
     (let [name (-> (str ns)
                    (str/split delimiter)
                    (second))
           opts (transform-singleton name opts)]
       (cond-> []
         (include-route? opts ::coll)  (conj (coll-route name ns opts))
         (include-route? opts ::unary) (conj (unary-route name ns opts))))
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
