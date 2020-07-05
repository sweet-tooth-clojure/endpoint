(ns sweet-tooth.endpoint.generate
  (:require [clojure.string :as str]
            [cljstache.core :as cs]
            [rewrite-clj.zip :as rz]
            [clojure.spec.alpha :as s]))

;;------
;; generator helpers
;;------

(defn point-path-segments
  [{:keys [path]} {:keys [path-base] :as opts}]
  (into path-base (if (fn? path)
                    (path opts)
                    path)))

(defn point-path
  [point opts]
  (str/join "/" (point-path-segments point opts)))

;;------
;; point generators
;;------

;; specs

(s/def ::path (s/or :path-segments (s/coll-of string?)
                    :path-fn fn?))
(s/def ::strategy keyword?)
(s/def ::rewrite fn?)
(s/def ::template string?)

(defmulti generate-point-type :strategy)

(defmethod generate-point-type ::rewrite-file [_]
  (s/keys :req-un [::path ::rewrite ::strategy]))

(defmethod generate-point-type ::create-file [_]
  (s/keys :req-un [::path ::template ::strategy]))

(s/def ::point (s/multi-spec generate-point-type :strategy))
(s/def ::points (s/map-of keyword? ::point))

;; methods

(defmulti generate-point (fn [{:keys [strategy]} _opts] strategy))

(defmethod generate-point ::rewrite-file
  [{:keys [rewrite] :as point} opts]
  (let [file-path (point-path point opts)]
    (spit file-path (rz/root-string (rewrite (rz/of-file file-path) opts)))))

(defmethod generate-point ::create-file
  [{:keys [template] :as point} opts]
  (let [file-path (point-path point opts)]
    (.mkdirs (java.io.File. (str/join "/" (butlast (point-path-segments point opts)))))
    (spit file-path (cs/render template opts))))

;;------
;; packages
;;------

;; specs

(s/def ::package-name keyword?)
(s/def ::package-points (s/coll-of keyword?))
(s/def ::package-pair (s/tuple ::package-name ::package-points))
(s/def ::opts fn?)
(s/def ::package (s/keys :req-un [::points]
                         :opt-un [::opts]))

(s/def ::package*-arg (s/or :package-name ::package-name
                            :package-pair ::package-pair
                            :package      ::package))

;; methods / fns

(defmulti package identity)

(defn package*
  [pkg]
  (let [conformed (s/conform ::package*-arg pkg)]
    (when (= :clojure.spec.alpha/invalid conformed)
      (throw (ex-info "Invalid package" {:package pkg
                                         :spec    (s/explain-data ::package*-arg pkg)})))
    (let [[ptype] conformed]
      (case ptype
        :package-name (package pkg)
        :package-pair (update (package (first pkg)) select-keys (second pkg))
        :package      pkg))))

;;------
;; generate
;;------
(defn generate
  [package & args]
  (let [{:keys [opts points]} (package* package)
        opts                  ((or opts identity) args)]
    (doseq [point (vals points)]
      (generate-point point opts))))
