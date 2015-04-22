(ns nordschleife.auto-scale-test
  (:require [nordschleife.auto-scale :refer :all :as as]
            [clojure.test :refer :all])
  (:import [org.jclouds.rackspace.autoscale.v1.domain GroupConfiguration LaunchConfiguration CreateScalingPolicy]))

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
  "A simple launch configuration."
  (.. (LaunchConfiguration/builder)
      (type LAUNCH_SERVER)
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
                    (launch-config test-launch-config))))
  (testing "Maps get converted to LaunchConfigurations"
    (let [from-map (launch-config {:load-balancers []
                                   :networks []
                                   :personalities []
                                   :server-disk-config "test disk config"
                                   :server-flavor-ref "test flavor"
                                   :server-image-ref "test image"
                                   :server-metadata {"test" "metadata"}
                                   :server-name "testy mctest"})]
      (is (= from-map test-launch-config)))))

(def test-scaling-policy
  (.. (CreateScalingPolicy/builder)
      (cooldown 30)
      (name "xyzzy")
      (target "-5.3")
      (targetType PERCENT_CHANGE)
      (build)))

(deftest scaling-policy-tests
  (testing "ScalingPolicies get passed through verbatim"
    (is (identical? test-scaling-policy (scaling-policy test-scaling-policy))))
  (testing "Maps get converted to ScalingPolicies"
    (let [from-map (scaling-policy {:cooldown 30
                                    :name "xyzzy"
                                    :target "-5.3"
                                    :target-type PERCENT_CHANGE})]
      (is (= from-map test-scaling-policy)))))

(deftest kw-to-sym-tests
  (are [kw expected] (= (@#'as/kw-to-sym kw) expected)
    :a 'a
    :a-b 'aB
    :a-bc-de 'aBcDe))

(deftest sym-to-kw-tests
  (are [sym expected] (= (@#'as/sym-to-kw sym) expected)
    'a :a
    'someMethod :some-method
    'someOtherMethod :some-other-method))
