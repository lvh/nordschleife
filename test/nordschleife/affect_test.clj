(ns nordschleife.affect-test
  (:require [nordschleife.affect :refer :all :as a]
            [nordschleife.auto-scale :as as]
            [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [nordschleife.scenarios :refer [scenario-gen]]))

(def some-setup
  {:name "test group"})

(def scale-up-event
  {:type :scale-up :amount 5})

(def scale-up-policy
  {:cooldown 0
   :name "scale-up by 5 policy for test group"
   :type as/WEBHOOK
   :target-type as/INCREMENTAL
   :target "5"})

(deftest required-policies-tests
  (are [evs expected] (= (@#'a/required-policies [some-setup evs])
                         expected)
       []
       #{}

       [{:type :acquiesce}]
       #{}

       [scale-up-event]
       #{scale-up-policy}

       (repeat 3 scale-up-event)
       #{scale-up-policy}))

(defspec required-policies-spec
  1000
  (prop/for-all
   [scenario scenario-gen]
   (let [[_ evs] scenario
         policies (@#'a/required-policies scenario)]
     (and
      (set? policies)
      (every? #(and (= (:cooldown %) 0)
                    (= (:type %) as/WEBHOOK))
              policies)
      (every? (comp #{as/INCREMENTAL
                      as/PERCENT_CHANGE
                      as/DESIRED_CAPACITY}
                    :target-type)
              policies)))))
