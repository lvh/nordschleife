(defproject nordschleife "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0-alpha5"]

                 ;; Typing is pretty great
                 [org.clojure/core.typed "0.2.84"]

                 ;; Generative testing
                 [org.clojure/test.check "0.7.0"]
                 [com.gfredericks/test.chuck "0.1.16"]

                 ;; Web server/client
                 [aleph "0.4.0-beta3"]

                 ;; Cloud interaction
                 [com.palletops/pallet "0.8.0-RC.11"]
                 [com.palletops/pallet-jclouds "1.7.3"]
                 [com.palletops/clj-jclouds "0.1.1"]
                 [org.apache.jclouds/jclouds-allcompute "1.7.3"]
                 [org.apache.jclouds.labs/rackspace-autoscale-us "1.7.3"]

                 ;; Configuration
                 [environ "1.0.0"]

                 ;; Asynchronous interactions
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]

                 ;; Logging
                 [com.taoensso/timbre "3.4.0"]]
  :plugins [[lein-cljfmt "0.1.10"]]
  :main ^:skip-aot nordschleife.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
