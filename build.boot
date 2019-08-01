(set-env!
  :source-paths   #{"src"}
  :target-path    "target/build"
  :dependencies   '[[org.clojure/clojure        "1.10.0"    :scope "provided"]
                    [boot/core                  "2.5.5"     :scope "provided"]
                    [adzerk/bootlaces           "0.1.13"    :scope "test"]
                    [adzerk/boot-test           "1.1.1"     :scope "test"]
                    [medley                     "0.7.1"]
                    [metosin/reitit-core        "0.3.9"]
                    [metosin/reitit-ring        "0.3.9"]

                    [duct/core           "0.7.0"]
                    [duct/module.logging "0.4.0" :scope "test"]
                    [duct/module.web     "0.7.0" :scope "test"]

                    ;; server
                    [io.clojure/liberator-transit "0.3.0"]
                    [com.flyingmachine/liberator-unbound "0.2.0"]
                    [com.flyingmachine/datomic-booties "0.1.7"]
                    [com.flyingmachine/datomic-junk "0.2.3"]
                    [com.flyingmachine/webutils "0.1.6"]
                    [liberator "0.15.3"]
                    [com.datomic/datomic-free "0.9.5344" :scope "provided"]
                    [ring-middleware-format "0.7.0"]
                    ;; [metosin/muuntaja "0.6.4"]
                    [buddy/buddy-auth "2.1.0"]])

(require
  '[adzerk.bootlaces :refer :all]
  '[adzerk.boot-test :refer :all])

(def +version+ "0.6.1-SNAPSHOT")
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

(deftask push-release-without-gpg
  "Deploy release version to Clojars without gpg signature."
  [f file PATH str "The jar file to deploy."]
  (comp
    (#'adzerk.bootlaces/collect-clojars-credentials)
    (push
      :file           file
      :tag            (boolean #'adzerk.bootlaces/+last-commit+)
      :gpg-sign       false
      :ensure-release true
      :repo           "deploy-clojars")))
