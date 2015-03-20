(ns nordschleife.scenarios-test
  (:require [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [clojure.test :refer :all]
            [nordschleife.scenarios :refer :all]))

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

   ;; The group cooldown is reasonable.
   (<= 0 (-> setup :group-config :cooldown) 10)

   ;; The group name is valid.
   (re-find #"nordschleife test group [a-zA-Z0-9]{12}"
            (-> setup :group-config :name))

   ;; The group min and max are valid.
   (let [{{min :min-entities max :max-entities} :group-config} setup]
     (<= 0 min max 10))

   ;; There are never two acquiesces after each other.
   (not-any? #(apply = :acquiesce %)
             (partition 2 1 (map :type events)))

   ;; Scenarios can't start by acquiescing.
   (not= (:type (first events)) :acquiesce)))
