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

;; -------------------------
;; specs
;; -------------------------

;; A segment is a pair of [:segment-type val]. A response consists of
;; 0 or more segments.
(s/def ::segment-key keyword?)
(s/def ::segment-val any?)
(s/def ::segment (s/tuple ::segment-key ::segment-val))
(s/def ::segments (s/coll-of ::segment :kind vector?))

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

(s/def ::raw-body (s/or :segments        ::segments
                        :segment         ::segment
                        :entity          ::entity
                        :entities        ::entities
                        :possible-entity ::possible-entity
                        :mixed-vector    ::mixed-vector))

;;---
;; formatted response
;;---

(defn- get-format-type
  [response]
  (get-in response [:sweet-tooth.endpoint/format ::formatter]))

(s/def :sweet-tooth.endpoint.format.raw/body any?)
(s/def :sweet-tooth.endpoint.format.segments/body ::segments)

(defmulti format-type get-format-type)
(defmethod format-type ::raw [_]
  (s/keys :req-un [:sweet-tooth.endpoint.format.raw/body]))
(defmethod format-type ::segments [_]
  (s/keys :req-un [:sweet-tooth.endpoint.format.segments/body]))

(s/def ::formatted-response (s/multi-spec format-type get-format-type))

;; -------------------------
;; format
;; -------------------------

(defn- format-entity
  [entity {:keys [id-key]}]
  {(-> entity meta :ent-type) (eu/key-by id-key (if (map? entity) [entity] entity))})


;;---
;; format segment
;;---

(defmulti format-segment (fn [segment-type _segment-value _format-opts] segment-type))
(defmethod format-segment :entity
  [segment-type segment-value format-opts]
  [segment-type
   (if (empty? segment-value)
     {}
     (format-entity segment-value format-opts))])

(defmethod format-segment :default
  [segment-type segment-value _]
  [segment-type segment-value])


;;---
;; format other shapes
;;---

(defn- format-possible-entity
  "Decorates map (or vector of maps) with metadata so that it can get
  formatted as an entity segment"
  [body {:keys [ent-type] :as format-opts}]
  (format-segment :entity (with-meta body {:ent-type ent-type}) format-opts))

(defn- format-mixed-vector
  [body conformed format-opts]
  (mapv (fn [x x-conformed]
          (when (not-empty x)
            (first (format-body x x-conformed format-opts))))
        body
        (second conformed)))

;;---
;; response formatting
;;---

(defn format-body
  [body conformed format-opts]
  (case (first conformed)
    :segments          body
    :segment           [body]
    :entity            [(format-segment :entity body format-opts)]
    :entities          [(format-segment :entity body format-opts)]
    :possible-entity   [(format-possible-entity body format-opts)]
    :possible-entities [(format-possible-entity body format-opts)]
    :mixed-vector      (format-mixed-vector body conformed format-opts)))

(s/fdef format-body
  :ret ::segments)

(defn format-segments-response
  "Assumes that the default response from endpoints is a map or vector
  of maps of a single ent-type. Formats that so that it conforms to
  ::segments."
  [{:keys [body] :as response}]
  (let [body        (if (sequential? body)
                      (vec (filter #(or (and % (not (coll? %)))
                                        (not-empty %))
                                   body))
                      body)
        format-opts (:sweet-tooth.endpoint/format response)
        conformed   (s/conform ::raw-body body)]
    (if (= ::s/invalid conformed)
      response
      (assoc response :body (format-body body conformed format-opts)))))

(defn format-response
  "Formats a response body to consist of segments unless formatter
  is ::raw."
  [response]
  (case (get-in response [:sweet-tooth.endpoint/format ::formatter])
    ::raw      response
    ::segments (format-segments-response response)
    nil        response))

(s/fdef forat-response
  :ret ::formatted-response)
