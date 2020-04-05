(defproject sweet-tooth/sweet-tooth-endpoint "0.7.8"
  :description "Utilities for working with liberator-based endpoints"
  :url "https://github.com/sweet-tooth-clojure/sweet-tooth-endpoint"
  :scm {:url "https://github.com/sweet-tooth-clojure/sweet-tooth-endpoint"}
  :license {:name "MIT"
            :url  "https://opensource.org/licenses/MIT"}

  :dependencies [[org.clojure/clojure   "1.10.0" :scope "provided"]
                 [medley                "0.7.1"]
                 [meta-merge            "1.0.0"]
                 [metosin/reitit-core   "0.3.9"]
                 [metosin/reitit-ring   "0.3.9"]

                 [duct/core             "0.7.0"]
                 [duct/middleware.buddy "0.1.0"]
                 [duct/module.logging   "0.4.0"]
                 [duct/module.web       "0.7.0"]

                 [sweet-tooth/describe "0.3.0"]

                 ;; server
                 [com.flyingmachine/liberator-unbound "0.2.0"]
                 [com.flyingmachine/datomic-booties "0.1.7"]
                 [com.flyingmachine/datomic-junk "0.2.3"]
                 [com.flyingmachine/webutils "0.1.6"]
                 [liberator "0.15.3"]
                 [ring-middleware-format "0.7.0"]
                 [bk/ring-gzip "0.3.0"]
                 [buddy/buddy-auth "2.1.0"]]

  :profiles {:dev {:dependencies   [[com.datomic/datomic-free "0.9.5344"]]
                   :resource-paths #{"test-resources"}}})
