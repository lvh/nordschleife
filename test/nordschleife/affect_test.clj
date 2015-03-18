(ns nordschleife.affect-test
  (:require [nordschleife.affect :refer :all :as a]
            [nordschleife.auto-scale :as as]
            [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [nordschleife.scenarios :refer [scenario-gen]]
            [taoensso.timbre :refer [spy]]))

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

(def scale-down-event
  {:type :scale-down :amount 5})

(def scale-down-policy
  {:cooldown 0
   :name "scale-down by 5 policy for test group"
   :type as/WEBHOOK
   :target-type as/INCREMENTAL
   :target "-5"})

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
       #{scale-up-policy}

       [scale-down-event]
       #{scale-down-policy}

       (repeat 3 scale-down-event)
       #{scale-down-policy}))

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
              policies)
      (every?
       (fn [policy]
         (let [[target target-type] ((juxt :target :target-type)
                                     policy)
               [_ sign amount] (re-find #"(-?)(\d+)" target)
               amount (Integer/parseInt amount)
               event-type (condp = [sign target-type]
                            ["" as/INCREMENTAL] :scale-up
                            ["-" as/INCREMENTAL] :scale-down
                            ["" as/PERCENT_CHANGE] :scale-up-pct
                            ["-" as/PERCENT_CHANGE] :scale-down-pct
                            ["" as/DESIRED_CAPACITY] :scale-to)]
           (some #{{:type event-type :amount amount}} evs)))
       policies)))))
