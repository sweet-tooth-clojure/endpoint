(ns sweet-tooth.endpoint.generate
  "Write code generators that can be executed from the REPL"
  (:require [clojure.string :as str]
            [cljstache.core :as cs]
            [rewrite-clj.zip :as rz]
            [clojure.spec.alpha :as s]))

;;------
;; generator helpers
;;------

(defn point-path-segments
  [{:keys [path]} {:keys [path-base] :as opts
                   :or {path-base []}}]
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
;; generators
;;------

;; specs

(s/def ::generator-name keyword?)
(s/def ::generator-point-names (s/coll-of keyword?))
(s/def ::generator-pair (s/tuple ::generator-name ::generator-point-names))
(s/def ::opts fn?)
(s/def ::generator (s/keys :req-un [::points]
                           :opt-un [::opts]))

(s/def ::generator*-arg (s/or :generator-name ::generator-name
                              :generator-pair ::generator-pair
                              :generator      ::generator))

;; methods / fns

(defmulti generator identity)

(defn generator*
  [pkg]
  (let [conformed (s/conform ::generator*-arg pkg)]
    (when (= :clojure.spec.alpha/invalid conformed)
      (throw (ex-info "Invalid generator" {:generator pkg
                                           :spec    (s/explain-data ::generator*-arg pkg)})))
    (let [[ptype] conformed]
      (case ptype
        :generator-name (generator pkg)
        :generator-pair (update (generator (first pkg)) select-keys (second pkg))
        :generator      pkg))))

;;------
;; generate
;;------
(defn generate
  [generator & args]
  (let [{:keys [opts points]} (generator* generator)
        opts                  ((or opts identity) args)]
    (doseq [point (vals points)]
      (generate-point point opts))))
