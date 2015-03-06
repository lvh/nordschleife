(ns nordschleife.auto-scale-test
  (:require [nordschleife.auto-scale :refer :all]
            [clojure.test :refer :all])
  (:import [org.jclouds.rackspace.autoscale.v1.domain GroupConfiguration]))

(deftest group-config-tests
  (testing "GroupConfiguration gets passed through verbatim"
    (let [in (.. (GroupConfiguration/builder)
                 (name "my group")
                 (cooldown 10)
                 (minEntities 0)
                 (maxEntities 10)
                 (metadata {"xyzzy" "iddqd"})
                 (build))
          out (group-config in)]
      (is (identical? in out)))))
