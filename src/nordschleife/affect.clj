(ns nordschleife.affect
  (:require [clojure.core.async :as a]
            [nordschleife.gathering :refer [gather]]))

(defn set-repeatedly
  "Sets the target to (f) repeatedly."
  [delay f target]
  (let [should-close (atom false)]
    (a/thread
      (loop []
        (reset! target (f))
        (a/<!! (a/timeout delay))
        (if  @should-close
          nil
          (recur))))
    #(reset! should-close true)))

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

(def max-fruitless-tries
  10)

(defmethod affect :acquiesce
  [{compute :compute} state-ref {desired :desired-state}]
  (let [get-state #(block-until-updated state-ref)]
    (loop [prev (get-state)
           curr (get-state)
           tries-left max-fruitless-tries
           total-tries 0]
      (let [progress (measure-progress prev curr desired)
            {progress? :progress? done? :done?} progress]
        (cond
          done? {:acquiesced? :true}
          progress? (recur curr (get-state) max-fruitless-tries (inc total-tries))
          (pos? tries) (recur curr (get-state) (dec tries) (inc total-tries))
          :default (throw RuntimeException))))))

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
  (let [state-ref (atom nil)
        stop-updating (set-repeatedly 10000 gather state-ref)
        perform (partial perform-scenario services state-ref)
        in (a/to-chan scenarios)
        xform (map perform-scenario)
        out (a/chan)]
    (a/pipeline-blocking parallelism out xform in)
    (a/<!! (a/into [] out))))
