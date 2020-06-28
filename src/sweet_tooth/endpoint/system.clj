(ns sweet-tooth.endpoint.system
  "Introduces some conveniences for dealing with duct systems:

  * A multimethod `config` for naming integrant configs, like `:dev`,
  `:test`, etc.
  * An `ig/init-key` alternative that allows a component's *configuration*
    to specify an alternative component implementation, possibly bypassing
    the `ig/init-key` implementation entirely
  * `replacement` and `shrubbery-mock` alternatives"
  (:require [integrant.core :as ig]
            [meta-merge.core :as mm]
            [shrubbery.core :as shrub]
            [medley.core :as medley]
            [clojure.spec.alpha :as s]))

;; -------------------------
;; specs
;; -------------------------

;;---
;; alternative components
;;---

(s/def ::init-key-alternative keyword?)
(s/def ::replacement any?)


(defmulti replacement-type ::init-key-alternative)
(defmethod replacement-type ::replacement [_]
  (s/keys :req [::init-key-alternative ::replacement]))
(defmethod replacement-type ::shrubbery-mock [_]
  (s/keys :req [::init-key-alternative ::shrubbery-mock]))

(s/def ::alternative-component (s/multi-spec replacement-type ::init-key-altnernative))

(defn component-spec-with-alternative
  "Alternative components are likely run in dev and test environments
  where components are unlikely to take the same arguments as their
  live counterparts. This helper function capture that notion for
  component specs."
  [live-spec]
  (s/or ::alternative-component ::alternative-component
        ::live-component        live-spec))

;;---
;; systems
;;---

(s/def ::init-keys (s/coll-of keyword? :kind vector?))
(s/def ::config-name keyword?)
(s/def ::config map?)
(s/def ::system map?)


;; -------------------------
;; provide alternative component impls inline
;; -------------------------

;;---
;; replacement
;;---

(defmulti init-key-alternative (fn [_ {:keys [::init-key-alternative]}]
                                 init-key-alternative))

(defn replacement
  "Retuns a component config that's used to return the given component
  without initializing the replaced component."
  [component]
  {::replacement          component
   ::init-key-alternative ::replacement})

(s/fdef replacement
  :ret ::alternative-component)

;; Simple replacement with an alternative component.
(defmethod init-key-alternative ::replacement
  [_ {:keys [::replacement]}]
  replacement)

;;---
;; mock components
;;---

(s/def ::shrubbery-mock-map map?)
(s/def ::shrubbery-mock-object-opts map?)
(s/def ::shrubbery-mocked-component-opts any?)
(s/def ::shrubbery-mock-tuple (s/tuple ::shrubbery-mock-object-opts
                                       ::shrubbery-mocked-component-opts))
(s/def ::shrubbery-mock-opts (s/or :map ::shrubbery-mock-map
                                   :tuple ::shrubbery-mock-tuple))

(defn shrubbery-mock
  "Returns a component configuration that will use
  `init-key-alternative`'s `::shrubbery-mock` implementation. Does not
  replace the original component's config so that the mocked
  component can be initialized.

  `::mocked-component-opts` defines additional config opts that should
  get passed to the mocked component. One use for this is to satisfy
  that component's config spec."
  ([] (shrubbery-mock {}))
  ([opts]
   (let [[opts-type] (s/conform ::shrubbery-mock-opts opts)]
     (case opts-type
       :map   (merge {::init-key-alternative ::shrubbery-mock
                      ::shrubbery-mock       (dissoc opts ::mocked-component-opts)}
                     (::mocked-component-opts opts))
       :tuple (merge {::init-key-alternative  ::shrubbery-mock
                      ::shrubbery-mock        (first opts)}
                     (second opts))))))

(s/fdef shrubbery-mock
  :ret ::alternative-component)

;; mock a component by initializating the mocked component, returning a
;; record that's used to create a mock object
(defmethod init-key-alternative ::shrubbery-mock
  [mocked-component {:keys [::shrubbery-mock] :as shrubbery-config}]
  (let [record           (ig/init-key mocked-component (dissoc shrubbery-config ::shrubbery-mock ::init-key-alternative))
        protocols        (set (shrub/protocols record))
        proto-impls      (medley/filter-keys protocols shrubbery-mock)
        dumb-proto-impls (medley/filter-keys keyword? shrubbery-mock)]
    (apply shrub/mock (reduce (fn [mock-impls protocol]
                                (into mock-impls [protocol (or (proto-impls protocol) dumb-proto-impls)]))
                              []
                              protocols))))

;;---
;; custom init
;;---

(defmethod init-key-alternative :default [_ _] nil)

(defn init-key
  "Allows component _configuration_ to specify alterative component
  implementations."
  [k v]
  (or (init-key-alternative k v)
      (ig/init-key k v)))

(defn init
  "Like integrant.core/init but allows config values specify how to
  provide an alternative implementation for a component. The
  alternative will be used instead of whatever would have gotten
  returned by `ig/init-key`. This makes it much easier to e.g. mock a
  component."
  ([config]
   (init config (keys config)))
  ([config init-keys]
   {:pre [(map? config)]}
   (ig/build config init-keys init-key #'ig/assert-pre-init-spec ig/resolve-key)))

(s/fdef init
  :args (s/cat :config ::config
               :init-keys ::init-kes)
  :ret ::system)

;; -------------------------
;; create named configs
;; -------------------------

(defmulti config
  "Provides a way for client application to name different integrant configs,
  e.g. :test, :dev, :prod, etc"
  identity)

(s/fdef config
  :args (s/cat :config-name keyword?)
  :ret ::config)

(defn- system-config
  ([config-name]
   (config config-name))
  ([config-name custom-config]
   (let [cfg (config config-name)]
     (cond (map? custom-config) (mm/meta-merge cfg custom-config)
           (fn? custom-config)  (custom-config cfg)))))

(s/fdef system-config
  :args (s/cat :config-name keyword?
               :custom-config ::config)
  :ret ::config)

(defn system
  ([config-name]
   (init (system-config config-name)))
  ([config-name custom-config]
   (init (system-config config-name custom-config)))
  ([config-name custom-config init-keys]
   (init (system-config config-name custom-config) init-keys)))

(s/fdef system
  :args (s/cat :config-name keyword?
               :custom-config ::config
               :init-keys ::init-keys)
  :ret ::system)

;; -------------------------
;; readers to use with duct/read-config
;; -------------------------

(def readers
  {'st/replacement    replacement
   'st/shrubbery-mock shrubbery-mock})
