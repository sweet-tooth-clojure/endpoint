(ns sweet-tooth.endpoint.system
  "Introduces some conveniences for dealing with duct systems:

  * A multimethod `config` for naming integrant configs, like `:dev`,
  `:test`, etc.
  * An `ig/init-key` alternative that allows a component's *configuration*
    to specify an alternative coponent implementation, possibly bypassing
    the `ig/init-key` implementation entirely
  * `replacement` and `shrubbery-mock` alternatives"
  (:require [integrant.core :as ig]
            [meta-merge.core :as mm]
            [shrubbery.core :as shrub]
            [medley.core :as medley]))

;; -------------------------
;; provide alternative component impls inline
;; -------------------------

;;---
;; replacement
;;---
(defn replacement
  "Retuns a component config that's used to return the given component
  without initializing the replaced component."
  [component]
  {::replacement          component
   ::init-key-alternative ::replacement})

(defmulti init-key-alternative (fn [_ {:keys [::init-key-alternative]}]
                                 init-key-alternative))

;; Simple replacement with an alternative component.
(defmethod init-key-alternative ::replacement
  [_ {:keys [::replacement]}]
  replacement)

;;---
;; mock components
;;---
(defn shrubbery-mock
  "Returns a component configuration that will use
  `init-key-alternative`'s `::shrubbery-mock` implementation. Does not
  replace the original component's config so that the mocked
  component can be initialized.

  `::mocked-component-opts` defines additional config opts that should
  get passed to the mocked component. One use for this is to satisfy
  that component's config spec."
  ([] (shrubbery-mock {}))
  ([{:keys [::mocked-component-opts] :as opts}]
   (merge {::init-key-alternative ::shrubbery-mock
           ::shrubbery-mock       (dissoc opts ::mocked-component-opts)}
          mocked-component-opts)))

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
  ([config keys]
   {:pre [(map? config)]}
   (ig/build config keys init-key #'ig/assert-pre-init-spec ig/resolve-key)))

;; -------------------------
;; create named configs
;; -------------------------

(defmulti config
  "Provides a way for client application to name different integrant configs,
  e.g. :test, :dev, :prod, etc"
  identity)

(defn system
  [config-name & [custom-config]]
  (let [cfg (config config-name)]
    (init (cond (not custom-config)  cfg
                (map? custom-config) (mm/meta-merge cfg custom-config)
                (fn? custom-config)  (custom-config cfg)))))

;; -------------------------
;; readers to use with duct/read-config
;; -------------------------

(def readers
  {'st/replacement    replacement
   'st/shrubbery-mock shrubbery-mock})
