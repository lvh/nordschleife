(ns nordschleife.convergence-test
  (:require [nordschleife.convergence :refer :all]
            [clojure.test :refer :all])
  (:import [org.jclouds.compute.domain.internal NodeMetadataImpl]))

(defn ^:private create-servers
  "Creates some dummy servers for testing."
  [n]
  (for [i (range n)]
    (NodeMetadataImpl. nil (str i) (str i) nil nil nil nil nil nil nil nil nil nil nil nil nil nil nil)))

(deftest measure-progress-tests
  (testing "If the capacity moves closer to the desired, progress has been made."
    (let [prev-state {:servers (create-servers 2)}
          curr-state {:servers (create-servers 3)}
          desired-state {:capacity 5}]
      (is (= (measure-progress prev-state curr-state desired-state)
             1)))))
