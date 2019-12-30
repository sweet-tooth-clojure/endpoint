(ns sweet-tooth.endpoint.format
  (:require [clojure.spec.alpha :as s]
            [sweet-tooth.endpoint.utils :as eu]))

(s/def ::item-key keyword?)
(s/def ::item-val any?)
(s/def ::item (s/tuple ::item-key ::item-val))
(s/def ::formatted-response (s/coll-of ::item :kind vector?))

(s/def ::entity (s/and (comp :ent-type meta) map?))
(s/def ::entities (s/and (comp :ent-type meta) (s/coll-of map?)))

(s/def ::possible-entity map?)
(s/def ::possible-entities (s/coll-of ::possible-entity))

(s/def ::unformatted-vector (s/coll-of (s/or :item              ::item
                                             :entity            ::entity
                                             :entities          ::entities
                                             :possible-entity   ::possible-entity
                                             :possible-entities ::possible-entities)
                                       :kind vector?))

(s/def ::raw-response (s/or :formatted-response ::formatted-response
                            :item               ::item
                            :entity             ::entity
                            :entities           ::entities
                            :possible-entity    ::possible-entity
                            :possible-entities  ::possible-entities
                            :unformatted-vector ::unformatted-vector))

;; can look up namespace in context map?
(defn format-entity
  [e id-key]
  [:entity (if (empty? e)
             {}
             {(:ent-type (meta e)) (eu/key-by id-key (if (map? e) [e] e))})])

(defn format-possible-entity
  [body id-key ent-type]
  ;; TODO log a warning that we're doing some magic bs here
  (-> body
      (with-meta {:ent-type ent-type})
      (format-entity id-key)))

(defn format-body
  [body id-key ent-type conformed]
  (case (first conformed)
    :formatted-response body
    :item               [body]
    :entity             [(format-entity body id-key)]
    :entities           [(format-entity body id-key)]
    :possible-entity    [(format-possible-entity body id-key ent-type)]
    :possible-entities  [(format-possible-entity body id-key ent-type)]
    :unformatted-vector (mapv (fn [x x-conformed]
                                (when (not-empty x)
                                  (first (format-body x id-key ent-type x-conformed))))
                              body
                              (second conformed))))

(defn format-response
  "Assumes that the default response from endpoints is a map or vector
  of maps of a single ent-type. Formats that so that it conforms to
  response."
  [{:keys [body] :as response}]

  (let [body                      (if (sequential? body)
                                    (vec (filter #(or (and % (not (coll? %)))
                                                      (not-empty %))
                                                 body))
                                    body)
        {:keys [id-key ent-type]} (:sweet-tooth.endpoint/format response)
        conformed                 (s/conform ::raw-response body)]
    (if (= ::s/invalid conformed)
      response
      (assoc response :body (format-body body id-key ent-type conformed)))))
