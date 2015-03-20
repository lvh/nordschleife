(ns nordschleife.affect-test
  (:require [nordschleife.affect :refer :all :as a]
            [nordschleife.auto-scale :as as]
            [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [nordschleife.scenarios :refer [scenario-gen]]
            [taoensso.timbre :refer [spy]])
  (:import [org.jclouds.rackspace.autoscale.v1 AutoscaleApi]
           [org.jclouds.rackspace.autoscale.v1.features GroupApi PolicyApi]
           [org.jclouds.rackspace.autoscale.v1.domain Group]))

(def some-setup
  {:name "test group"})

(def scale-up-event
  {:type :scale-up :amount 5})

(def scale-up-policy
  (as/scaling-policy
   {:cooldown 0
    :name "scale-up by 5 policy for test group"
    :type as/WEBHOOK
    :target-type as/INCREMENTAL
    :target "5"}))

(def scale-down-event
  {:type :scale-down :amount 5})

(def scale-down-policy
  (as/scaling-policy
   {:cooldown 0
    :name "scale-down by 5 policy for test group"
    :type as/WEBHOOK
    :target-type as/INCREMENTAL
    :target "-5"}))

(def scale-up-pct-event
  {:type :scale-up-pct :amount 5})

(def scale-up-pct-policy
  (as/scaling-policy
   {:cooldown 0
    :name "scale-up-pct by 5 policy for test group"
    :type as/WEBHOOK
    :target-type as/PERCENT_CHANGE
    :target "5"}))

(def scale-down-pct-event
  {:type :scale-down-pct :amount 5})

(def scale-down-pct-policy
  (as/scaling-policy
   {:cooldown 0
    :name "scale-down-pct by 5 policy for test group"
    :type as/WEBHOOK
    :target-type as/PERCENT_CHANGE
    :target "-5"}))

(def scale-to-event
  {:type :scale-to :amount 5})

(def scale-to-policy
  (as/scaling-policy
   {:cooldown 0
    :name "scale-to by 5 policy for test group"
    :type as/WEBHOOK
    :target-type as/DESIRED_CAPACITY
    :target "5"}))

(deftest required-policies-tests
  (are [evs expected] (= (@#'a/required-policies [some-setup evs])
                         expected)
       []
       #{}

       [{:type :acquiesce}]
       #{}

       [scale-up-event]
       #{scale-up-policy}

       (repeat 3 scale-up-event)
       #{scale-up-policy}

       [scale-down-event]
       #{scale-down-policy}

       (repeat 3 scale-down-event)
       #{scale-down-policy}

       [scale-up-pct-event]
       #{scale-up-pct-policy}

       (repeat 3 scale-up-pct-event)
       #{scale-up-pct-policy}

       [scale-down-pct-event]
       #{scale-down-pct-policy}

       (repeat 3 scale-down-pct-event)
       #{scale-down-pct-policy}

       [scale-to-event]
       #{scale-to-policy}

       (repeat 3 scale-to-event)
       #{scale-to-policy}

       (concat (repeat 3 scale-up-event)
               (repeat 3 scale-down-event)
               (repeat 3 scale-up-pct-event)
               (repeat 3 scale-down-pct-event)
               (repeat 3 scale-to-event))
       #{scale-up-policy
         scale-down-policy
         scale-up-pct-policy
         scale-down-pct-policy
         scale-to-policy}))

(defn ^:private policy-has-matching-event?
  [events policy]
  (let [[_ sign amount] (re-find #"(-?)(\d+)" (.getTarget policy))
        amount (Integer/parseInt amount)
        event-type (condp = [sign (.getTargetType policy)]
                     ["" as/INCREMENTAL] :scale-up
                     ["-" as/INCREMENTAL] :scale-down
                     ["" as/PERCENT_CHANGE] :scale-up-pct
                     ["-" as/PERCENT_CHANGE] :scale-down-pct
                     ["" as/DESIRED_CAPACITY] :scale-to)]
    (boolean (some #(= (select-keys % [:type :amount])
                       {:type event-type :amount amount})
                   events))))

(defspec required-policies-spec
  1000
  (prop/for-all
   [scenario scenario-gen]
   (let [[_ evs] scenario
         policies (@#'a/required-policies scenario)]
     (and
      (set? policies)
      (every? #(and (= (.getCooldown %) 0)
                    (= (.getType %) as/WEBHOOK))
              policies)
      (every? (comp #{as/INCREMENTAL
                      as/PERCENT_CHANGE
                      as/DESIRED_CAPACITY}
                    #(.getTargetType %))
              policies)
      (every? (partial policy-has-matching-event? evs) policies)))))

(defn create-test-services
  []
  (let [state (atom {:groups-by-id {}
                     :executed-policies-by-group-id {}})
        group-api (reify GroupApi
                    (create [_ group-config launch-config policies]
                      (let [id (str (gensym "test group "))
                            group (-> (Group/builder)
                                      (.id id)
                                      (.groupConfiguration group-config)
                                      (.launchConfiguration launch-config)
                                      (.scalingPolicy policies)
                                      (.build))]
                        (swap! state assoc-in [:groups-by-id id] group)
                        group)))
        api (reify AutoscaleApi
              (getGroupApiForZone [_ _]
                group-api)
              (getPolicyApiForZoneAndGroup [_ _ group-id]
                (reify PolicyApi
                  (execute [_ policy-id]
                    (update-in state group-id policy-id (fnil inc 0))))))]
    {:auto-scale api :state state}))

(defspec prep-scenario-spec
  (let [test-services (create-test-services)
        prep (partial @#'a/prep-scenario test-services)]
    (prop/for-all
     [scenario scenario-gen]
     (not (nil? (:group (first (prep scenario)))))
     (let [[_ events] scenario]
       (every? (partial policy-has-matching-event? events)
               (vals (:policy-index (first (prep scenario)))))))))
