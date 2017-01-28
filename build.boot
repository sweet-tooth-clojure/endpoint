(set-env!
  :source-paths   #{"src"}
  :target-path    "target/build"
  :dependencies   '[[org.clojure/clojure        "1.7.0"     :scope "provided"]
                    [boot/core                  "2.5.5"     :scope "provided"]
                    [adzerk/bootlaces           "0.1.13"    :scope "test"]
                    [adzerk/boot-test           "1.1.1"     :scope "test"]
                    [adzerk/boot-cljs           "1.7.228-1" :scope "provided"]
                    [adzerk/boot-reload         "0.4.13"]

                    ;; server
                    [io.clojure/liberator-transit "0.3.0"]
                    [com.flyingmachine/liberator-unbound "0.1.1"]
                    [com.flyingmachine/datomic-junk "0.2.3"]
                    [com.flyingmachine/webutils "0.1.6"]
                    [com.datomic/datomic-free "0.9.5344" :scope "provided"]
                    [buddy "1.0.0"]])

(require
 '[adzerk.bootlaces :refer :all]
 '[adzerk.boot-test :refer :all])

(def +version+ "0.1.0-SNAPSHOT")
(bootlaces! +version+)

(task-options!
 pom  {:project     'sweet-tooth/sweet-tooth-endpoint
       :version     +version+
       :description "Utilities for working with liberator-based endpoints"
       :url         "https://github.com/sweet-tooth-clojure/sweet-tooth-workflow"
       :scm         {:url "https://github.com/sweet-tooth-clojure/sweet-tooth-workflow"}
       :license     {"MIT" "https://opensource.org/licenses/MIT"} })

;; local dev and test
(deftask ldev
  []
  (comp (watch)
        (repl :server true)))

(deftask make-install
  "local install"
  []
  (comp (pom)
        (jar)
        (install)))
