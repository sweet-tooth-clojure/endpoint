(ns sweet-tooth.endpoint.routes.reitit
  "Allow specifying routes using keywords corresponding to namespaces"
  (:require [clojure.string :as str]
            [integrant.core :as ig]
            #?@(:cljs [[goog.string :as gstr]
                       [goog.string.format]])))

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
    [(#?(:clj format :cljs gstr/format) "%s/%s" path-prefix (slash endpoint-name))
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
    [(#?(:clj format :cljs gstr/format) "%s/%s/{%s}" path-prefix (slash endpoint-name) (subs (str id-key) 1))
     (merge {:name  (keyword endpoint-name)
             ::ns   ns
             ::type ::unary}
            opts
            unary-opts)]))

(defn ns-pair->ns-route
  "In a pair of [n m], if n is a keyword then it's strated as a "
  [[ns opts :as pair]]
  (if (string? ns)
    [pair]
    (let [endpoint-name (str/replace (name ns) #".*?endpoint\." "")
          path          (str "/" endpoint-name)]
      (cond-> []
        (include-route? opts ::coll)  (conj (coll-route endpoint-name ns opts))
        (include-route? opts ::unary) (conj (unary-route endpoint-name ns opts))))))

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
