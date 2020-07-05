(ns sweet-tooth.endpoint.generate
  (:require [clojure.string :as str]
            [cljstache.core :as cs]
            [rewrite-clj.zip :as rz]
            [sweet-tooth.endpoint.generate.endpoint :as sge]))

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
    (spit file-path (cs/render template opts))))


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
  [package-name & args]
  (let [{:keys [opts points]} (package package-name)
        opts                  ((or opts identity) args)]
    (doseq [point (vals points)]
      (generate-point point opts))))
