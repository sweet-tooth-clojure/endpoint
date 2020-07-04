(ns sweet-tooth.endpoint.generate
  (:require [clojure.string :as str]
            [rewrite-clj.zip :as rz]
            [sweet-tooth.endpoint.generate.endpoint :as sge]
            [sweet-tooth.endpoint.system :as es]))

;;------
;; generator helpers
;;------

(defn point-path-segments
  [{:keys [path]} {:keys [src-base] :as opts}]
  (into src-base (if (fn? path)
                   (path opts)
                   path)))

(defn point-path
  [point opts]
  (str/join "/" (point-path-segments point opts)))


;;------
;; point generators
;;------

(defmulti generate-point (fn [{:keys [strategy]} _opts] strategy))

;; modify file

(defn modify-file
  "Updates a file using the modifications from a point"
  [{:keys [modify form] :as point} opts]
  (let [file-path (point-path point opts)]
    (spit file-path (rz/root-string (modify (rz/of-file file-path)
                                            (form opts))))))

(defmethod generate-point ::modify-file
  [point opts]
  (modify-file point opts))

(defmethod generate-point ::create-file
  [{:keys [template] :as point} opts]
  (let [file-path (point-path point opts)]
    (.mkdirs (java.io.File. (str/join "/" (butlast (point-path-segments point opts)))))
    (spit file-path template)))


;;------
;; packages
;;------
(defmulti package identity)
;; TODO explore package naming and ns deps. we require sge only so that we can
;; register its package here. but it would be nice if there were some other way
;; to make packages discoverable
(defmethod package :sweet-tooth/endpoint [_] sge/package)

;;------
;; generate
;;------
(defn generate
  [package-name {:keys [:config-name project-ns] :as opts}]
  (let [pkg           (package package-name)
        points        (select-keys (:points pkg) (or (:points opts) (:default-points pkg)))
        config        (es/config (or config-name :dev))
        project-ns    (or project-ns (:duct.core/project-ns config))
        generate-opts (merge {:project-ns project-ns
                              :src-base   ["src" project-ns]}
                             opts)
        opts          (merge generate-opts ((:opts pkg identity) generate-opts))]
    (doseq [point (vals points)]
      (generate-point point opts))))
