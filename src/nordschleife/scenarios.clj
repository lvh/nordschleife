(ns nordschleife.scenarios
  (:require [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.generators :as gen']))

(defn ^:private weighted-consts
  [weights-and-consts]
  (gen/frequency
   (for [[freq const] (seq weights-and-consts)]
     [freq (gen/return const)])))

(def scale-down
  "A generator for scale down events.")

(def server-failure
  "A generator for server failures."
  (gen/tuple (gen/return :server-failures)
             (weighted-consts [[10 1] [2 2] [1 3]])))

(def event-gen
  "A generator for scenario events."
  (gen/frequency [[10 (gen/return :acquiesce)]

                  [6 (gen/return :scale-up)]
                  [1 (gen/return :scale-up-%)]

                  [6 (gen/return :scale-down)]
                  [1 (gen/return :scale-down-%)]

                  [6 server-failure]]))
