(ns nordschleife.affect
  (:require [clojure.core.async :as a]))

(defn repeatedly
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
  [{compute :compute} _]
  (loop []))

(defmethod affect :scale-up
  [{auto-scale :auto-scale} {amount :amount}])

(defmethod affect :scale-down
  [{auto-scale :auto-scale} {amount :amount}])

(defn perform-scenario
  [services scenario]
  (let [affect (partial affect services)]
    (map affect scenario)))

(defn perform-scenarios
  [services scenarios parallelism]
  (let [perform (partial perform-scenario services)
        in (a/to-chan scenarios)
        xform (map perform)
        out (a/chan)]
    (a/pipeline-blocking 1 out xform in)
    out))
