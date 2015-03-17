(ns nordschleife.affect-test
  (:require [nordschleife.affect :refer :all :as a]
            [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [nordschleife.scenarios :refer [scenario-gen]]))

(def some-setup
  {})

(deftest required-policies-tests
  (are [evs expected] (= (@#'a/required-policies [some-setup evs])
                         expected)
    [] #{}
    [{:type :acquiesce}] #{}
    [{:type :scale-up :amount 5}] #{{:type :scale-up :amount 5}}
    (repeat 3 {:type :scale-up :amount 5}) #{{:type :scale-up :amount 5}}))

(defspec required-policies-spec
  1000
  (prop/for-all
   [scenario scenario-gen]
   (let [[_ evs] scenario
         policies (@#'a/required-policies scenario)]
     (and (every? (comp #{:scale-up :scale-down :scale-to} :type) policies)
          (every? (into #{} evs) policies)))))
