(ns nordschleife.scenarios-test
  (:require [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [clojure.test :refer :all]
            [nordschleife.scenarios :refer :all]))

;; Subvert ^:private fns
(def coalesce-acquiesces
  @#'nordschleife.scenarios/coalesce-acquiesces)

(deftest coalesce-acquiesces-tests
  (testing "no adjacent coalesces"
    (are [evs] (= (coalesce-acquiesces evs) evs)
      []
      (take 10 (cycle [:a :b :c :d]))))
  (testing "adjacent coalesces"
    (are [in-evs out-evs] (= (coalesce-acquiesces in-evs) out-evs)
      (repeat 10 {:type :acquiesce})
      [{:type :acquiesce}]

      (concat (repeat 5 {:type :scale-up :amount 5})
              (repeat 5 {:type :acquiesce})
              (repeat 5 :y))
      (into [] (concat (repeat 5 {:type :scale-up :amount 5})
                       [{:type :acquiesce}]
                       (repeat 5 :y)))
      (concat (repeat 5 {:type :scale-up :amount 5})
              (repeat 5 {:type :acquiesce})
              (repeat 5 :y)
              (repeat 5 {:type :acquiesce})
              (repeat 5 :z))
      (concat (repeat 5 {:type :scale-up :amount 5})
              [{:type :acquiesce}]
              (repeat 5 :y)
              [{:type :acquiesce}]
              (repeat 5 :z)))))

(defspec scenarios-properties
  1000
  (prop/for-all
   [[setup events] scenario-gen]
   ;; The launch configuration is always the fixed, known-good one.
   ;; Note that jClouds requires more args than Auto Scale does.
   (is (= (:launch-config setup)
          {:load-balancers []
           :networks [service-net]
           :personalities []
           :server-name (str "server for " (-> setup :group-config :name))
           :server-image-ref debian-base
           :server-flavor-ref "general1-1"
           :server-disk-config "AUTO"
           :server-metadata {}}))

   ;; The group cooldown is reasonable.
   (is (<= 0 (-> setup :group-config :cooldown) 10))

   ;; The group name is valid.
   (is (re-find #"nordschleife group [a-zA-Z0-9]{12}"
                (-> setup :group-config :name)))

   ;; The group min and max
   (let [{{min :min-entities max :max-entities} :group-config} setup]
     (is (<= 0 min max 10)))

   ;; There are never two acquiesces after each other.
   (is (not-any? #(apply = :acquiesce %)
                 (partition 2 1 (map :type events))))

   ;; Scenarios never start by acquiescing.
   (is (not= (:type (first events)) :acquiesce))

   ;; Scenarios always end by acquiescing.
   (is (= (:type (last events)) :acquiesce))

   ;; All events know how much capacity they're expecting.
   (let [{{min :min-entities max :max-entities} :group-config} setup]
     (is (every? (fn [ev] (<= min (-> ev :desired-state :capacity) max)) events)))))
