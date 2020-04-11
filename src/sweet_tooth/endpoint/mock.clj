(ns sweet-tooth.endpoint.mock
  (:require [clojure.string :as str]
            [integrant.core :as ig]
            [shrubbery.core :as shrub]))

(defmulti protocol-mocks
  "Define the protocol that a component implements in order to mock it"
  (fn [k _] k))

(defmethod protocol-mocks :default [_ _] nil)

(defmulti object
  "Return a object and optional impls used to introspect for mocking"
  (fn [k proto-impls] k))

(defmethod object :default [_ _] nil)

(defn object->proto-impls
  [obj]
  (when obj
    (let [the-obj   (if (sequential? obj) (first obj) obj)
          impls     (if (sequential? obj) (second obj) {})
          protocols (shrub/protocols the-obj)]
      (reduce (fn [proto->impls protocol]
                (assoc proto->impls protocol (get impls protocol {})))
              {}
              protocols))))

(defmethod ig/init-key ::mock-component [k config]
  (let [mock-map (or (protocol-mocks k config)
                     (object->proto-impls (object k config))

                     ;; default to using the original init-key, under
                     ;; the assumption it will return an object that
                     ;; implements protocols that we want to mock
                     (object->proto-impls [(ig/init-key (-> (str k)
                                                            (str/replace #"-mock$" "")
                                                            (keyword))
                                                        {})
                                           ;; config should be proto-impls
                                           config]))]
    ;; TODO validation
    (apply shrub/mock (->> mock-map (into []) flatten))))
