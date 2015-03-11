(ns nordschleife.convergence
  (:require [com.palletops.jclouds.compute2 :refer :all])
  (:import [org.jclouds.compute.domain.internal NodeMetadataImpl]
           [org.jclouds.compute.domain NodeMetadata]))

(defmacro status-const
  [status]
  `(. org.jclouds.compute.domain.NodeMetadata$Status ~status))

(defn ^:private abs
  [n]
  (max n (- n)))

(defn ^:private live
  "Gets the live servers in a state."
  [state]
  (filter (some-fn running? pending?) (:servers state)))

(defn measure-progress
  "Measures progress between states towards the desired state."
  [prev-state curr-state desired-state]
  (let [prev-cap (count (live prev-state))
        curr-cap (count (live curr-state))
        delta (- curr-cap prev-cap)
        dsrd-cap (:capacity desired-state)]
    (cond
      (= curr-cap dsrd-cap) {:progress? true :done? true}
      (< prev-cap dsrd-cap curr-cap) {:progress? false
                                      :reason "overshoot"}
      (< curr-cap dsrd-cap prev-cap) {:progress? false
                                      :reason "undershoot"}
      (or (and (pos? delta) (> curr-cap dsrd-cap))
          (and (neg? delta) (< curr-cap dsrd-cap))
          (zero? delta)) {:progress? false}
      :default {:progress? true :amount (abs delta)})))
