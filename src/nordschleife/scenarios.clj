(ns nordschleife.scenarios
  "Tools for generating testing scenarios."
  (:require [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.generators :as gen']))

(defn ^:private weighted-consts
  [weights-and-consts]
  (gen/frequency
   (for [[freq const] (seq weights-and-consts)]
     [freq (gen/return const)])))

(def events-with-amounts
  (for [[weight t amount-weights] [[6 :scale-up [[10 1] [2 2] [1 3]]]
                                   [1 :scale-up-pct [[10 5] [2 10]]]
                                   [6 :scale-down [[10 1] [2 2] [1 3]]]
                                   [1 :scale-down-pct [[10 5] [2 10]]]
                                   [6 :server-failures [[10 1] [2 2] [1 3]]]]]
    [weight
     (gen'/for [n (weighted-consts amount-weights)]
       {:type t :amount n})]))

(def event-gen
  "A generator for scenario events."
  (gen/frequency (into [[10 (gen/return {:type :acquiesce})]]
                       events-with-amounts)))

(def scenario-gen
  "A generator for scenarios."
  (gen/vector event-gen 4 10))

(defn coalesce-acquiesces
  [scenario]
  (reduce (fn [xs x]
            (if (= (:type x) (:type (last xs)) :acquiesce)
              xs (conj xs x)))
          []
          scenario))
