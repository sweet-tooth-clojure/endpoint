(ns sweet-tooth.endpoint.validate-config
  "not production ready"
  (:require [clojure.pprint :as pprint]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [com.rpl.specter :as specter]
            [duct.core :as duct]
            [duct.core.env :as env]))

;;------
;; env vars
;;------

(defn duct-env-reader
  [spec]
  {:duct/env spec
   :val      (env/env spec)})

(defn read-config-with-env-vars
  "Reads the config leaving duct/env values intact in order to report
  the original config"
  ([source]
   (read-config-with-env-vars source {}))
  ([source readers]
   (duct/read-config source (merge {'duct/env duct-env-reader}
                                   readers))))

(def COMPACTING-WALKER
  "Used to prune all paths that don't terminate in :duct/env. Thanks to
  @nathanmarz for the help."
  (specter/recursive-path [] p
                          (specter/cond-path #(and (map %) (:duct/env %)) specter/STAY
                                             #(= (type %) integrant.core.Ref) specter/STAY
                                             map? [(specter/compact specter/MAP-VALS) p]
                                             coll? [(specter/compact specter/ALL) p]
                                             specter/STAY specter/STAY)))

(defn missing-env-var?
  [x]
  (and (map? x)
       (:duct/env x)
       (or (nil? (:val x))
           (and (string? x) (str/blank? x)))))

(defn missing-env-vars
  "get all missing env vars"
  [config]
  (let [vars (specter/select [(specter/walker missing-env-var?) :duct/env] config)]
    (when (and (not= vars :com.rpl.specter.impl/NONE)
               (not-empty vars))
      (->> vars sort (into [])))))

(defn missing-env-var-config
  [config]
  (specter/setval [COMPACTING-WALKER (complement missing-env-var?)] specter/NONE config))

(defn missing-env-var-suggested-config
  [config]
  (some->> (missing-env-var-config config)
           (specter/setval [(specter/walker missing-env-var?)] "SET VALUE HERE")))

(defn missing-env-var-report
  [config]
  (when-let [vars (missing-env-vars config)]
    (let [suggested-config (missing-env-var-suggested-config config)]
      (format (str "Your config defines these env vars but they have no value:\n"
                   "%s\n\n"
                   "You can hard-code these in your config with the following:\n"
                   "%s")
              (str/join " " vars)
              (binding [*print-namespace-maps* false]
                (with-out-str (pprint/pprint suggested-config)))))))

;;------
;; generic validation -- experimental!
;;------

(s/def ::raw-config any?)
(s/def ::actual-config any?)
(s/def ::level #{:warn :error})
(s/def ::msg string?)

(s/def ::key-validation (s/keys :req-un [::raw-config
                                         ::actual-config
                                         ::level
                                         ::errors]
                                :opt-un [::msg]))

(s/def ::key-validations (s/nilable (s/coll-of ::key-valdation)))

(defn read-config
  "Reads the config leaving duct/env values intact in order to report
  the original config"
  ([source]
   (read-config source {}))
  ([source readers]
   (duct/read-config source (merge {'duct/env #(into ['env-var] %)}
                                   readers))))

(defmulti validate-config-key
  ""
  (fn [k env _config] [k env]))

(defmethod validate-config-key :default [_ _ _] nil)

(defn validation-errors
  [{:keys [duct.core/environment] :as config} raw-config]
  (reduce-kv (fn [errors component-key component-config]
               (or (some->> (validate-config-key component-key
                                                 environment
                                                 component-config)
                            (merge {:key           component-key
                                    :raw-config    (component-key raw-config)
                                    :actual-config component-config})
                            (conj errors))
                   errors))
             []
             config))

;;------
;; reporting validation
;;------

(defmulti msg (fn [error] (:component-key error)))

(defn default-msg
  [{:keys [msg error raw-config actual-config component-key]}]
  (or msg
      (format (str "Configuration error for %s:\n"
                   "- configured with: %s\n"
                   "- received config: %s\n"
                   "- error: %s\n")
              component-key
              raw-config
              actual-config
              error)))

(defmethod msg :default
  [error]
  (default-msg error))

(defn report
  "A string describing the errors"
  [errors]
  (let [sorted-errors (sort-by :component-key errors)]
    (format (str "Misconfigured components:\n"
                 "%s\n\n"
                 "Component configuration errors:\n"
                 "%s\n")
            (->> sorted-errors
                 (map #(str "- " (:component-key %)))
                 (str/join "\n"))
            (->> sorted-errors
                 (map msg)
                 (str/join "\n")))))
