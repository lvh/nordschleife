(ns nordschleife.affect
  (:require [clojure.core.async :as a]
            [nordschleife.auto-scale :as as]
            [nordschleife.gathering :refer [gather]]
            [nordschleife.convergence :refer [measure-progress]]))

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

(def max-fruitless-tries
  10)

(defmulti affect :type)

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
          progress? (recur curr
                           (get-state)
                           max-fruitless-tries
                           (inc total-tries))
          (pos? tries-left) (recur curr
                                   (get-state)
                                   (dec tries-left)
                                   (inc total-tries))
          :default (throw RuntimeException))))))

(def ^:private event-type->target-type
  {:scale-up as/INCREMENTAL
   :scale-down as/INCREMENTAL

   :scale-up-pct as/PERCENT_CHANGE
   :scale-down-pct as/PERCENT_CHANGE

   :scale-to as/DESIRED_CAPACITY})

(def ^:private event-type->sign
  "Given the event type, what is the sign of the amount?

  The sign is encoded as a string: `-` if the sign is negative, the
  empty string otherwise."
  {:scale-up ""
   :scale-down "-"

   :scale-up-pct ""
   :scale-down-pct "-"

   :scale-to ""})

(def ^:private event-types-with-policies
  (into #{} (keys event-type->target-type)))

(defn ^:private event->target
  [event]
  (let [sign (event-type->sign (:type event))]
    (str sign (:amount event))))

(defn ^:private scale
  "Actually execute a scaling event."
  [{auto-scale :auto-scale} state-ref setup {amount :amount}])

(defmethod affect :scale-up
  [services _ setup event]
  (scale services _ setup event))

(defmethod affect :scale-down
  [services _ setup event]
  (scale services _ setup event))

(defmethod affect :scale-to
  [services _ setup event]
  (scale services _ setup event))

(defn ^:private required-policies
  "Finds the required policies in the given scenario."
  [[setup events]]
  (->> events
       (filter (comp event-types-with-policies :type))
       (map (fn [event]
              {:cooldown 0
               :type as/WEBHOOK
               :name (str (name (:type event)) " by " (:amount event)
                          " policy for " (:name setup))
               :target-type (event-type->target-type (:type event))
               :target (event->target event)}))
       (map as/scaling-policy)
       (into #{})))

(defn ^:private prep-scenario
  "Prepares the context the scenario needs to run.

  Returns the modified scenario, where the setup contains the
  necessary policy ids."
  [{auto-scale :auto-scale} scenario]
  (let [policies (required-policies scenario)
        api (as/group-api auto-scale "the zone")
        [setup events] scenario
        [gc lc] ((juxt :group-config :launch-config) setup)
        group (as/create-group api gc lc policies)
        created-policies (as/get-policies group)
        policy-idx (into {} (map (fn [policy]
                                   [[(.getTargetType policy) (.getTarget policy)]
                                    policy])
                                 created-policies))]
    [(merge setup {:group group :policy-index policy-idx})
     events]))

(defn perform-scenario
  [services state-ref scenario]
  (prep-scenario services scenario)
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
