(ns sweet-tooth.endpoint.format
  "Conforms endpoint response bodies to the format that the frontend expects.
  The Sweet Tooth frotend expects responses in the form of

  `[[:entity {:ent-type {ent-id {:entity :map}}}]]`

  The vector `[:entity ...]` is a segment. A response contains 0 or
  more segments. This format was chosen to better support composition
  in responses: If you need to add more information to a response, you
  can add a new segment without concern for what the response already
  contains.

  However, it would be tedious to have to format the response body of
  every single endpoint this way, when most the time you just want to
  return one or more entities. You should be able to just return the
  entities; this namespace lets you do that.

  Basically, returning a map or vector of maps is shorthand for the
  full entity segment."

  (:require [clojure.spec.alpha :as s]
            [sweet-tooth.endpoint.utils :as eu]))

(declare format-body)

;; A segment is a pair of [:segment-type val]. A response consists of
;; 0 or more segments.
(s/def ::segment-key keyword?)
(s/def ::segment-val any?)
(s/def ::segment (s/tuple ::segment-key ::segment-val))
(s/def ::formatted-response (s/coll-of ::segment :kind vector?))

;; Entity segment types get special treatment. They are the most
;; common segment type.
(s/def ::entity (s/and (comp :ent-type meta) map?))
(s/def ::entities (s/and (comp :ent-type meta) (s/coll-of map?)))

(s/def ::possible-entity map?)
(s/def ::possible-entities (s/coll-of ::possible-entity))

;; A response can be a mixed vector, allowing a response that
;; mixes unformatted maps with segments like:
;; [{:id 3}
;;  [:default {:current-user {}}]
;;  [{:id 5} {:id 6}]
;;  [:page {}]]
(s/def ::mixed-vector (s/coll-of (s/or :segment           ::segment
                                       :entity            ::entity
                                       :entities          ::entities
                                       :possible-entity   ::possible-entity
                                       :possible-entities ::possible-entities)
                                 :kind vector?))

(s/def ::raw-response (s/or :formatted-response ::formatted-response
                            :segment            ::segment
                            :entity             ::entity
                            :entities           ::entities
                            :possible-entity    ::possible-entity
                            :mixed-vector       ::mixed-vector))

(defn- format-entity
  "Entity maps are returned as

  {:ent-type {ent-id-1 {:ent :mp}
              ent-id-2 {:ent :mp}}}}}"
  [e id-key]
  [:entity (if (empty? e)
             {}
             {(:ent-type (meta e)) (eu/key-by id-key (if (map? e) [e] e))})])

(defn- format-possible-entity
  "Decorates map (or vector of maps) with metadata so that it can get
  formatted as an entity segment"
  [body id-key ent-type]
  (-> body
      (with-meta {:ent-type ent-type})
      (format-entity id-key)))

(defn- format-mixed-vector
  [body id-key ent-type conformed]
  (mapv (fn [x x-conformed]
          (when (not-empty x)
            (first (format-body x id-key ent-type x-conformed))))
        body
        (second conformed)))

(defn format-body
  [body id-key ent-type conformed]
  (case (first conformed)
    :formatted-response body
    :segment            [body]
    :entity             [(format-entity body id-key)]
    :entities           [(format-entity body id-key)]
    :possible-entity    [(format-possible-entity body id-key ent-type)]
    :possible-entities  [(format-possible-entity body id-key ent-type)]
    :mixed-vector       (format-mixed-vector body id-key ent-type conformed)))

(defn format-response
  "Assumes that the default response from endpoints is a map or vector
  of maps of a single ent-type. Formats that so that it conforms to
  response."
  [{:keys [body] :as response}]
  (if (::skip-format (meta body))
    response
    (let [body                      (if (sequential? body)
                                      (vec (filter #(or (and % (not (coll? %)))
                                                        (not-empty %))
                                                   body))
                                      body)
          {:keys [id-key ent-type]} (:sweet-tooth.endpoint/format response)
          conformed                 (s/conform ::raw-response body)]
      (if (= ::s/invalid conformed)
        response
        (assoc response :body (format-body body id-key ent-type conformed))))))

(defn wrap-skip-format
  "`skip-format` is a flag read by formatting middleware that indicates
  that the response should not be formatted."
  [f]
  (fn [req]
    (update (f req) :body with-meta {:sweet-tooth.endpoint.format/skip-format true})))
