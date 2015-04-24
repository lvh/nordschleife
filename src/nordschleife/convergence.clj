(ns nordschleife.convergence
  (:require [com.palletops.jclouds.compute2
             :refer [hostname running? pending?]]
            [bigml.sampling.simple :refer [sample]]))

(defn ^:private abs
  "Gets the absolute value of a number."
  [n]
  (max n (- n)))

(defn ^:private live
  "Gets the live servers in a state."
  [state]
  (let [live? (some-fn running? pending?)]
    (filter live? (:servers state))))

(defn has-group-name?
  "Does the node have the scaling group name?"
  [group-name node]
  (.contains (hostname node) group-name))

(defn filter-group
  "Filter state items that belong to group with given name."
  [group-name state]
  (let [has-this-group-name? (partial has-group-name? group-name)]
    (update state :servers (partial filter has-this-group-name?))))

(defn pick-victims
  "Find some servers to kill."
  [group-name state n]
  (->> (filter-group group-name state)
       :servers
       sample
       (take n)))

(defn measure-progress
  "Measures progress between states towards the desired state.

  This will look at all servers in the given state; if you'd like to
  check multiple scaling groups concurrently, filter before passing to
  this function."
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
