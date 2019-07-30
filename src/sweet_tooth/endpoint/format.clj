(ns sweet-tooth.endpoint.format
  (:require [sweet-tooth.endpoint.utils :as eu]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(s/def ::item-key keyword?)
(s/def ::item-val any?)
(s/def ::item (s/tuple ::item-key ::item-val))
(s/def ::formatted-response (s/coll-of ::item :kind vector?))

(s/def ::entity (s/and map? (comp :ent-type meta)))
(s/def ::entities (s/and (s/coll-of map?) (comp :ent-type meta)))

(s/def ::possible-entity map?)
(s/def ::possible-entities (s/coll-of ::possible-entity))

(s/def ::unformatted-vector (s/coll-of (s/or :item                ::item
                                             :entity              ::entity
                                             :entities            ::entities
                                             :possible-entity     ::possible-entity
                                             :possible-entitities ::possible-entities)
                                       :kind vector?))

(s/def ::raw-response (s/or :formatted-response  ::formatted-response
                            :item                ::item
                            :entity              ::entity
                            :entities            ::entities
                            :possible-entity     ::possible-entity
                            :possible-entitities ::possible-entities
                            :unformatted-vector  ::unformatted-vector))

;; can look up namespace in context map?
(defn format-entity
  [e id-key]
  [:entity (if (empty? e)
             {}
             {(:ent-type (meta e)) (eu/key-by id-key (if (map? e) [e] e))})])

(defn format-possible-entity
  [body id-key endpoint-ns]
  ;; TODO log a warning that we're doing some magic bs here
  (-> body
      (with-meta {:ent-type (keyword (str/replace (name endpoint-ns) #".*\.(?=[^.]+$)" ""))})
      (format-entity id-key)))


(defn format-body
  [body id-key endpoint-ns conformed]
  (case (first conformed)
    :formatted-response body
    :item               [body]
    :entity             [(format-entity body id-key)]
    :entities           [(format-entity body id-key)]
    :possible-entity    [(format-possible-entity body id-key endpoint-ns)]
    :possible-entities  [(format-possible-entity body id-key endpoint-ns)]
    :unformatted-vector (mapv (fn [[_ x :as x-conformed]]
                                (first (format-body x id-key endpoint-ns x-conformed)))
                              (second conformed))))

(defn format-ctx-body
  "Assumes that the default response from endpoints is a map or vector
  of maps of a single ent-type. Formats that so that it conforms to
  response."
  [{:keys [id-key response] :as ctx}]
  (let [{:keys [body]} response
        conformed      (s/conform ::raw-response body)
        endpoint-ns    (:sweet-tooth.endpoint/namspace ctx)]

    (when (= ::s/invalid conformed)
      (println "INVALID!"))

    (let [x (if (= ::s/invalid conformed)
              ctx
              (assoc-in ctx [:response :body] (format-body body id-key endpoint-ns conformed)))]
      (println "FORMATTED" x)
      x)))
