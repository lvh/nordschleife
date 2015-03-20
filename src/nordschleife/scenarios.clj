(ns nordschleife.scenarios
  "Tools for generating testing scenarios."
  (:require [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.generators :as gen']))

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
  (comp chop-head-acquiesce coalesce-acquiesces))

(def events-gen
  "A generator for sequences of scenario events, with some pointless
  interactions removed."
  (gen/fmap clean-events (gen/vector event-gen 4 10)))

(def scenario-gen
  "A generator for scenarios, which consist of a setup + some events."
  (gen/tuple setup-gen events-gen))
