(ns sweet-tooth.endpoint.handler
  (:require [ring.util.response :as resp]
            [integrant.core :as ig]))

(defmethod ig/init-key ::index.html
  [_ {:keys [root exclude]
      :or   {root    "public"
             exclude ["json"]}}]
  (fn [req]
    (let [content-type (str (get-in req [:headers "content-type"]))]
      (if (some #(re-find (re-pattern %) content-type) exclude)
        {:status 404}
        (-> (resp/resource-response "index.html" {:root root})
            (resp/content-type "text/html"))))))
