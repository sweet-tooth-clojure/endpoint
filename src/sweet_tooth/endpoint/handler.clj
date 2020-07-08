(ns sweet-tooth.endpoint.handler
  (:require [ring.util.response :as resp]
            [integrant.core :as ig]))

;; used with duct's error handler. With an SPA, you want to return
;; index.html for unknown routes because they could correspond to
;; valid frontend routes, and by loading index.html you give the
;; frontend app a chance to handle the route.
(defmethod ig/init-key ::index.html
  [_ {:keys [root exclude status]
      :or   {root    "public"
             exclude ["json"]
             status  200}}]
  (fn [req]
    (let [content-type (str (get-in req [:headers "content-type"]))]
      (if (some #(re-find (re-pattern %) content-type) exclude)
        {:status status}
        (-> (resp/resource-response "index.html" {:root root})
            (resp/content-type "text/html"))))))
