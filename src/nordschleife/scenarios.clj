(ns nordschleife.scenarios
  "Tools for generating testing scenarios."
  (:require [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.generators :as gen']
            [clojure.math.numeric-tower :as math]))

(def launch-config-gen
  (gen/elements
   [{:load-balancers []
     :networks []
     :personalities []
     :server-name "nordschleife test server "
     :server-image-ref "0938b7e9-ba56-4af2-a9e6-52c47d931d22"
     :server-flavor-ref "general1-1"
     :server-metadata {}}]))

(def group-config-gen
  (gen'/for [random-str (gen/no-shrink (gen'/string-from-regex #"[a-zA-Z0-9]{12}"))
             limits (gen/elements [{:cooldown 0
                                    :min-entities 0
                                    :max-entities 10}])]
    (-> limits
        (assoc :name (str "nordschleife test group " random-str)))))

(def setup-gen
  (gen'/for [group-config group-config-gen
             launch-config launch-config-gen]
    {:group-config group-config
     :launch-config launch-config}))

(defn ^:private weighted-consts
  [weights-and-consts]
  (gen/frequency
   (for [[freq const] (seq weights-and-consts)]
     [freq (gen/return const)])))

(def weighted-events
  (for [[w t pairs] [[6 :scale-up [[10 1]
                                   [2 2]
                                   [1 3]]]
                     [1 :scale-up-pct [[10 5]
                                       [2 10]]]
                     [6 :scale-down [[10 1]
                                     [2 2]
                                     [1 3]]]
                     [1 :scale-down-pct [[10 5]
                                         [2 10]]]
                     [2 :scale-to [[10 1]
                                   [5 5]
                                   [1 10]]]
                     [6 :server-failures [[10 1]
                                          [2 2]
                                          [1 3]]]]]
    [w (gen'/for [n (weighted-consts pairs)]
         {:type t :amount n})]))

(def event-gen
  "A generator for scenario events."
  (gen/frequency (into [[10 (gen/return {:type :acquiesce})]]
                       weighted-events)))

(defn add-tail-acquiesce
  [evs]
  (conj evs {:type :acquiesce}))

(defn coalesce-acquiesces
  [evs]
  (reduce (fn [evs ev]
            (if (= (:type ev) (:type (last evs)) :acquiesce)
              evs (conj evs ev)))
          []
          evs))

(defn chop-head-acquiesce
  "Acquiescing at the start is meaningless, so don't do it"
  [evs]
  (if (= (:type (first evs)) :acquiesce)
    (rest evs)
    evs))

(def clean-events
  (comp coalesce-acquiesces chop-head-acquiesce add-tail-acquiesce))

(def events-gen
  "A generator for sequences of scenario events, with some pointless
  interactions removed."
  (gen/fmap clean-events (gen/vector event-gen 4 10)))

(defn ^:private clamp
  [x low high]
  (-> x (max low) (min high)))

(defn ^:private compute-desired-states
  [[setup evs]]
  (let [capacity (fn [event] (or (-> event :desired-state :capacity) 0))
        [max-cap min-cap] ((juxt :min-entities :max-entities)
                           (:group-config setup))
        evs (reduce
             (fn [events event]
               (let [prev-cap (capacity (last events))
                     this-cap (condp = (:type event)
                                :scale-to (:amount event)
                                :scale-up (+ prev-cap (:amount event))
                                :scale-down (- prev-cap (:amount event))
                                prev-cap)
                     this-cap (clamp  this-cap min-cap max-cap)
                     event (assoc event :desired-state {:capacity this-cap})]
                 (conj events event)))
             []
             evs)]
    [setup evs]))

(def scenario-gen
  "A generator for scenarios, which consist of a setup + some events."
  (gen/fmap compute-desired-states (gen/tuple setup-gen events-gen)))

(defn ^:private round-away-from-zero
  "Round the way Auto Scale rounds."
  [x]
  (let [round (if (pos? x) math/ceil math/floor)]
    (round x)))
