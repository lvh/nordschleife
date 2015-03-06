(ns nordschleife.auto-scale-test
  (:require [nordschleife.auto-scale :refer :all]
            [clojure.test :refer :all])
  (:import [org.jclouds.rackspace.autoscale.v1.domain GroupConfiguration LaunchConfiguration]))

(def test-group-config
  (.. (GroupConfiguration/builder)
      (name "my group")
      (cooldown 10)
      (minEntities 0)
      (maxEntities 10)
      (metadata {"xyzzy" "iddqd"})
      (build)))

(deftest group-config-tests
  (testing "GroupConfiguration gets passed through verbatim"
    (is (identical? test-group-config
                    (group-config test-group-config))))
  (testing "Maps get converted to GroupConfigurations"
    (let [from-map (group-config {:name "my group"
                                  :cooldown 10
                                  :min-entities 0
                                  :max-entities 10
                                  :metadata {"xyzzy" "iddqd"}})]
      (is (= from-map test-group-config)))))

(def test-launch-config
  (.. (LaunchConfiguration/builder)
      (loadBalancers [])
      (networks [])
      (personalities [])
      (serverDiskConfig "test disk config")
      (serverFlavorRef "test flavor")
      (serverImageRef "test image")
      (serverMetadata {"test" "metadata"})
      (serverName "testy mctest")
      (build)))

(deftest launch-config-tests
  (testing "LaunchConfiguration gets passed through verbatim"
    (is (identical? test-launch-config
                    (launch-config test-launch-config)))))

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
