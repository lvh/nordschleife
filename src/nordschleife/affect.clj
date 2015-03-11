(ns nordschleife.affect
  (:require [clojure.core.async :as a]
            [nordschleife.gathering :refer [gather]]))

(defn set-repeatedly
  [delay f target]
  (a/thread
    (loop []
      (reset! target (f))
      (a/<!! (a/timeout delay))
      (recur))))

(defn ^:private block-until-updated
  "Blocks until given reference type is updated, returning the new value."
  [r]
  (let [c (a/chan)
        k ::block-until-updated]
    (add-watch r k (fn [_ _ _ new-state]
                     (remove-watch r k)
                     (a/>!! c new-state)))
    (a/<!! c)))

(defmulti affect :type)

(defmethod affect :acquiesce
  [{compute :compute} state-ref {desired-state :desired-state}]
  (let [get-state #(block-until-updated state-ref)])
  (loop [prev (get-state)
         curr (get-state)
         tries 10]
    (if (and (pos? tries))
      (recur curr (get-state) (dec tries)))))

(defmethod affect :scale-up
  [{auto-scale :auto-scale} state-ref {amount :amount}])

(defmethod affect :scale-down
  [{auto-scale :auto-scale} state-ref {amount :amount}])

(defn perform-scenario
  [services state-ref scenario]
  (let [affect (partial affect services state-ref)]
    (map affect scenario)))

(defn perform-scenarios
  [services scenarios parallelism]
  (let [perform (partial perform-scenario services state-ref)
        in (a/to-chan scenarios)
        xform (map perform)
        out (a/chan)]
    (a/pipeline-blocking parallelism out xform in)
    out))
