(ns nordschleife.affect
  (:require [clojure.core.async :as a]
            [nordschleife.auto-scale :as as]
            [nordschleife.gathering :refer [gather]]
            [nordschleife.convergence :refer [measure-progress]]
            [taoensso.timbre :refer [debug info spy]])
  (:import [org.jclouds.http HttpResponseException]
           [org.jclouds.rest AuthorizationException]))

(def zone "ORD")

(defn set-repeatedly
  "Sets the target to (f) repeatedly."
  [delay f target]
  (let [should-close (atom false)]
    (a/thread
      (loop []
        (info "Will repeatedly set state" f)
        (let [result (f)]
          (info "Setting state" result)
          (reset! target result))
        (a/<!! (a/timeout delay))
        (if @should-close
          (info "Done updating")
          (recur))))
    #(reset! should-close true)))

(defn ^:private block-until-updated
  "Blocks until given reference type is updated, returning the new value."
  [r]
  (let [c (a/chan)
        k (gensym)]
    (add-watch r k (fn [_ _ _ new-state]
                     (remove-watch r k)
                     (a/>!! c new-state)))
    (a/<!! c)))

(def max-fruitless-tries
  "How many times do we accept that no progress has been made yet?"
  10)

(defmulti affect
  "Apply a step."
  (fn [_ _ _ event]
    (let [t (:type event)]
      (if (#{:scale-up :scale-down :scale-to} t)
        :scale
        t))))

(defmethod affect :acquiesce
  "Wait until acquiesced, assert convergence happened."
  [{compute :compute} state-ref setup event]
  (let [get-state #(block-until-updated state-ref)
        {desired :desired-state} event]
    (info "Acquiescing" group-id)
    (loop [prev (get-state)
           curr (get-state)
           tries-left max-fruitless-tries
           total-tries 1]
      (info "Acquiescing" group-id
            "total tries" total-tries
            "tries left" tries-left)
      (let [progress (measure-progress prev curr desired)
            {progress? :progress? done? :done?} progress
            ctx {:total-tries total-tries
                 :group (-> setup :group-config :name)
                 :event event}]
        (cond
          done? (do
                  (info "Acquiesced!" group-id)
                  (merge ctx {:acquiesced? true}))
          progress? (do
                      (info "Made progress" group-id)
                      (recur curr
                             (get-state)
                             max-fruitless-tries
                             (inc total-tries)))
          (pos? tries-left) (do
                              (info "No progress" group-id)
                              (recur curr
                                     (get-state)
                                     (dec tries-left)
                                     (inc total-tries)))
          :default (do (info "Failed to acquiesce")
                       (merge ctx {:acquiesced? false})))))))

(def ^:private event-type->target-type
  "Translate the nordschleife event type into the one used by the
  jClouds Auto Scale library."
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

(defmethod affect :scale
  "Execute a scaling event."
  [{auto-scale :auto-scale} _ setup event]
  (info "Scaling" ((juxt :type :amount) event))
  (let [api (as/policy-api auto-scale zone (.getId (:group setup)))
        key [(event-type->target-type (:type event))
             (event->target event)]
        policy ((:policy-index setup) key)
        id (.getId policy)
        result (try {:success? (as/execute-policy api id)}
                    (catch HttpResponseException e
                      {:success? false
                       :reason (.getContent e)})
                    (catch AuthorizationException e
                      {:success? false
                       :reason (.getContent (.getCause e))}))]
    {:event event :result result :group (-> setup :group-config :name)}))

(defmethod affect :server-failures
  "Fake some server failures. Currently a no-op."
  [services state-ref setup event]
  {:event event})

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
        api (as/group-api auto-scale zone)
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
  (info "Performing scenario" scenario)
  (let [[setup evs] (spy (prep-scenario services scenario))
        affect (partial affect services state-ref setup)]
    (map affect evs)))

(defn perform-scenarios
  [services scenarios parallelism]
  (let [state-ref (atom nil)
        stop-updating (set-repeatedly 10000 gather state-ref)
        perform (partial perform-scenario services state-ref)
        in (a/to-chan scenarios)
        xform (map perform)
        out (a/chan)]
    (a/pipeline-blocking parallelism out xform in)
    (let [res (a/<!! (a/into [] out))]
      (stop-updating)
      (a/close! out)
      res)))
