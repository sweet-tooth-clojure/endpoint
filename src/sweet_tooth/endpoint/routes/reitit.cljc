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

(defn- slash
  "replace dots with slashes in domain name to create a string that's
  route-friendly"
  [name]
  (str/replace name #"\." "/"))

(defn coll-route
  [name ns opts]
  (let [coll-opts    (::coll opts)
        {:keys [path-prefix]
         :or   {path-prefix ""}
         :as   opts} (dissoc opts ::coll ::unary)]
    [(format-str "%s/%s" path-prefix (slash name))
     (merge {:name  (keyword (str name "s"))
             ::ns   ns
             ::type ::coll}
            opts
            coll-opts)]))

(defn unary-route
  [name ns opts]
  (let [unary-opts   (::unary opts)
        {:keys [path-prefix]
         :or   {path-prefix ""}
         :as   opts} (dissoc opts ::coll ::unary)
        id-key       (or (:id-key unary-opts)
                         (:id-key opts)
                         :id)]
    [(format-str "%s/%s/{%s}" path-prefix (slash name) (subs (str id-key) 1))
     (merge {:name  (keyword name)
             ::ns   ns
             ::type ::unary}
            opts
            unary-opts)]))

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
           path (str "/" name)]
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
