(defproject sweet-tooth/sweet-tooth-endpoint "0.7.11"
  :description "Utilities for working with liberator-based endpoints"
  :url "https://github.com/sweet-tooth-clojure/sweet-tooth-endpoint"
  :scm {:url "https://github.com/sweet-tooth-clojure/sweet-tooth-endpoint"}
  :license {:name "MIT"
            :url  "https://opensource.org/licenses/MIT"}

  :plugins [[lein-tools-deps "0.4.5"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]

  :lein-tools-deps/config {:config-files [:install :user :project]}

  :profiles {:dev {:lein-tools-deps/config {:aliases [:dev]}
                   :dependencies           [[com.datomic/datomic-free "0.9.5344"]]
                   :resource-paths         #{"test-resources"}}})
