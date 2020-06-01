(defproject sweet-tooth/sweet-tooth-endpoint "0.10.0"
  :description "Utilities for working with liberator-based endpoints"
  :url "https://github.com/sweet-tooth-clojure/sweet-tooth-endpoint"
  :scm {:url "https://github.com/sweet-tooth-clojure/sweet-tooth-endpoint"}
  :license {:name "MIT"
            :url  "https://opensource.org/licenses/MIT"}

  :dependencies [[org.clojure/clojure   "1.10.0" :scope "provided"]
                 [medley                "1.3.0"]
                 [meta-merge            "1.0.0"]
                 [metosin/reitit-core   "0.4.2"]
                 [metosin/reitit-ring   "0.4.2"]

                 [integrant             "0.8.0"]
                 [duct/core             "0.8.0"]
                 [duct/middleware.buddy "0.1.0"]
                 [duct/module.logging   "0.4.0"]
                 [duct/module.web       "0.7.0"]

                 [sweet-tooth/describe "0.3.0"]
                 [com.gearswithingears/shrubbery "0.4.1"]
                 [com.rpl/specter "1.1.3"]

                 [com.flyingmachine/liberator-unbound "0.2.0"]
                 [com.flyingmachine/datomic-booties "0.1.7"]
                 [com.flyingmachine/datomic-junk "0.2.3"]
                 [com.flyingmachine/webutils "0.1.6"]
                 [liberator "0.15.3"]
                 [ring-middleware-format "0.7.4"] ;; transit content negotiation
                 [bk/ring-gzip "0.3.0"]
                 [buddy/buddy-auth "2.1.0"]]

  :profiles {:dev {:dependencies   [[com.datomic/datomic-free "0.9.5344"]]
                   :resource-paths #{"test-resources"}}})
