(ns nordschleife.convergence-test
  (:require [nordschleife.convergence :refer :all]
            [clojure.test :refer :all]))

(defn ^:private create-servers
  "Creates some dummy servers for testing.

  TODO: These should look like jClouds' NodeMetadata."
  [n]
  (for [i (range n)]
    {:id (str i)}))

(deftest measure-progress-tests
  (testing "If the capacity moves closer to the desired, progress has been made."
    (let [prev-state {:servers (create-servers 2)}
          curr-state {:servers (create-servers 3)}
          desired-state {:capacity 5}]
      (is (= (measure-progress prev-state curr-state desired-state)
             1)))))
