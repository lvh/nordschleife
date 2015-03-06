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
      (is (identical? in out))))
  (testing "Maps get converted to GroupConfigurations"
    (let [from-map (group-config {:name "my group"
                                  :cooldown 10
                                  :min-entities 0
                                  :max-entities 10
                                  :metadata {"xyzzy" "iddqd"}})
          expected (.. (GroupConfiguration/builder)
                       (name "my group")
                       (cooldown 10)
                       (minEntities 0)
                       (maxEntities 10)
                       (metadata {"xyzzy" "iddqd"})
                       (build))]
      (is (= from-map expected)))))

(deftest kw-to-sym-tests
  (are [kw expected] (= (kw-to-sym kw) expected)
       :a 'a
       :a-b 'aB
       :a-bc-de 'aBcDe))

(deftest sym-to-kw-tests
  (are [sym expected] (= (sym-to-kw sym) expected)
       'a :a
       'someMethod :some-method
       'someOtherMethod :some-other-method))
