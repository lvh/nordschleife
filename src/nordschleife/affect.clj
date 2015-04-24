(ns nordschleife.affect
  (:require [clojure.core.async :as a]
            [nordschleife.auto-scale :as as]
            [nordschleife.gathering :refer [gather]]
            [nordschleife.convergence :as c]
            [taoensso.timbre :as t]
            [manifold.stream :as s]
            [com.palletops.jclouds.compute2 :refer [destroy-nodes-matching]])
  (:import [org.jclouds.http HttpResponseException]
           [org.jclouds.rest AuthorizationException]
           [java.util.concurrent Executors]
           [java.util Date]))

(def zone
  "ORD")

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
  "Which kinds of events have policies?"
  (into #{} (keys event-type->target-type)))

(defn ^:private event->target
  "Given an event, what is the target of the event?

  The target is jClouds-speak for the amount to scale by, be it
  absolute, relative, or a relative percentage."
  [event]
  (let [sign (event-type->sign (:type event))]
    (str sign (:amount event))))

(def affect nil)
(defmulti affect
  "Apply a step."
  (fn [_ _ event]
    (let [t (:type event)]
      (if (#{:scale-up :scale-down :scale-to} t)
        :scale
        t))))

(def max-tries
  "How many times do we accept a lack of process?"
  10)

(defmethod affect :acquiesce
  [{state-stream :state-stream}
   {{name :name} :group-config}
   {desired :desired-state :as event}]
  (t/info "Acquiescing" name)
  (let [measure (fn [[prev curr]]
                  (c/measure-progress prev curr desired))]
    (->> state-stream
         (s/stream->seq)
         (take max-tries)
         (partition 2 1)
         (map measure)
         (filter :progress?)
         (assoc {:event event :group name} :result))))

(defmethod affect :scale
  [{auto-scale :auto-scale} setup event]
  (t/info "Scaling" ((juxt :type :amount) event))
  (let [group (:group setup)
        api (as/policy-api auto-scale zone (.getId group))
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
    {:event event
     :result result
     :group (-> setup :group-config :name)}))

(defmethod affect :server-failures
  [services
   {{name :name} :group-config}
   {amount :amount :as event}]
  (t/info "Faking" amount "server failures for group" name)
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
        [setup events] scenario
        group (as/create-group (as/group-api auto-scale zone)
                               (:group-config setup)
                               (:launch-config setup)
                               policies)
        created-policies (as/get-policies group)
        policy-idx (->> created-policies
                        (map (fn [policy]
                                [[(.getTargetType policy) (.getTarget policy)]
                                 policy]))
                        (into {}))]
    [(merge setup {:group group
                   :policy-index policy-idx})
     events]))

(defn ^:private clean-up
  "Cleans up the scaling group and associated resources."
  [{:keys [state-stream compute auto-scale]}
   {:keys [group]}]
  (let [name (as/group-name group)
        in-this-group? (partial c/has-group-name? name)]
    (t/info "Cleaning up group" name)
    (destroy-nodes-matching compute in-this-group?)
    (as/delete-group auto-scale group)))

(defn ^:private perform-scenario
  "Execute a single scenario and clean up afterwards."
  [services scenario]
  (let [[setup evs] (prep-scenario services scenario)
        do-step (partial affect services setup)]
    (try
      (into [] (map do-step evs))
      (finally
        (clean-up services setup)))))

(defn perform-scenarios
  "Execute multiple scenarios with given parallelism."
  [services scenarios parallelism]
  (let [state-stream (s/periodically 10000 #(gather services))
        services (assoc services :state-stream state-stream)
        out (a/chan)]
    (a/pipeline-blocking parallelism
                         out
                         (map (partial perform-scenario services))
                         (a/to-chan scenarios))
    (let [res (a/<!! (a/into [] out))]
      (t/info "Received all outputs, closing state stream")
      (s/close! state-stream)
      res)))
