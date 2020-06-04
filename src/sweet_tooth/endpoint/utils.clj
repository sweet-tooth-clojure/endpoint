(ns sweet-tooth.endpoint.utils)
;; TODO move this to sweet-tooth.core

(defn update-vals
  "Takes a map to be updated, x, and a map of
  {[k1 k2 k3] update-fn-1
   [k4 k5 k6] update-fn-2}
  such that such that k1, k2, k3 are updated using update-fn-1
  and k4, k5, k6 are updated using update-fn-2"
  [x update-map]
  (reduce (fn [x [keys update-fn]]
            (reduce (fn [x k] (update x k update-fn))
                    x
                    keys))
          x
          update-map))

;; -------------------------
;; Organize response records for easy frontend consumption
;; -------------------------
(defn key-by
  [k xs]
  (into {} (map (juxt k identity) xs)))

(defn ent-type
  "add ent-type metadata to obj. ent-type should be a keyword."
  [x y]
  {:pre [(or (keyword? x) (keyword? y))]}
  ;; allow for both `->` and `->>`
  (if (keyword? x)
    (with-meta y {:ent-type x})
    (with-meta x {:ent-type y})))
