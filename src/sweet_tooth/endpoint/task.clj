(ns sweet-tooth.endpoint.task
  (:require [integrant.core :as ig]
            [clojure.stacktrace :as stacktrace]))

(defn run-task [ig-config task-name & [ex-handler]]
  (try (let [system (ig/init ig-config [task-name])
             result ((task-name system))]
         (ig/halt! system)
         result)
       (catch Exception e (if ex-handler
                            (ex-handler e)
                            (throw e)))))

(defn- print-and-rethrow
  [e]
  (stacktrace/print-stack-trace e)
  (flush)
  (throw e))

(defn run-task-final
  [ig-config task-name & [ex-handler]]
  (let [exh (or ex-handler print-and-rethrow)]
    (try (let [result (run-task ig-config task-name exh)]
           (if (= ::failed result)
             (System/exit 1)
             (System/exit 0)))
         (finally (System/exit 1)))))
