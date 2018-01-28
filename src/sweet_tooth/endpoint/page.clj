(ns sweet-tooth.endpoint.page
  (:require [sweet-tooth.endpoint.utils :as u]
            [sweet-tooth.endpoint.liberator :as el]))

(defn slice
  "Get a single page of ents out of a collection"
  [page per-page ents]
  (->> ents
       (drop (* (dec page) per-page))
       (take per-page)))

(defprotocol PageSortFn
  "Which comparison function to use function to use for some value"
  (asc [x] "Ascending fn")
  (desc [x] "Descending fn"))

(extend-protocol PageSortFn
  java.util.Date
  (asc [x] #(.before %1 %2))
  (desc [x] #(.after %1 %2))

  java.lang.Object
  (asc [x] compare)
  (desc [x] #(compare %2 %1))

  nil
  (asc [x] compare)
  (desc [x] #(compare %2 %1)))

(defn sort-fn
  [val sort-order]
  ((if (= sort-order :desc) desc asc) val))

(defn paginate
  [p ents]
  (let [{:keys [page per-page sort-order type]} p
        ent-count (count ents)
        data (cond->> ents
               (:sort-by p) (sort-by (:sort-by p) (sort-fn ((:sort-by p) (first ents)) sort-order))
               true (slice page per-page))]
    {:entity {type (u/key-by :db/id data)}
     :page {:query {(:query-id p) p}
            :result {p {:total-pages (Math/round (Math/ceil (/ ent-count per-page)))
                        :ent-count ent-count
                        :ordered-ids (map :db/id data)}}}}))

(defn page-to-new
  [new-ent-id page ents]
  (let [ent-page (paginate page ents)]
    (if (get-in ent-page [:entity (:type page) new-ent-id])
      ent-page
      (let [[[query-id page-query]] (vec (-> ent-page :page :query))]
        (paginate (assoc page-query :page (get-in ent-page [:page :result page-query :total-pages])) ents)))))

(defn organize-page-data
  [ent-type page]
  (update-in page [:entity ent-type] #(u/key-by :db/id %)))

;; TODO spec this
(defn page-params
  "Get pagination-related params. Includes base set of allowable
  params, plus whatever user specifies in `allowed-keys`.

  Converts `:page` and `:per-page` to integer.
  TODO have route coerce params correctly."
  [ctx & allowed-keys]
  (-> (el/params ctx)
      (select-keys (into [:page :per-page :sort-order :sort-by :query-id :type]
                         allowed-keys))
      (u/update-vals {[:page :per-page] #(Integer. %)
                      [:sort-by :sort-order :query-id :type] #(keyword (subs % 1))})))
