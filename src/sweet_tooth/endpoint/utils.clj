(ns sweet-tooth.endpoint.utils)

;; util
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

(defn remove-nils-from-map
  [record]
  (into {} (remove (comp nil? second) record)))

;; -------------------------
;; Organize response records for easy frontend consumption
;; -------------------------
(defn key-by
  [k xs]
  (into {} (map (juxt k identity) xs)))

(defn format-ent
  "Expects `e`, be it map or seq, to have ent-type defined in metadata"
  [e id-key]
  {(:ent-type (meta e)) (key-by id-key (if (map? e) [e] e))})

(defn ent-type
  [x y]
  (if (keyword? x)
    (with-meta y {:ent-type x})
    (with-meta x {:ent-type y})))
