(ns nordschleife.scenarios-test
  (:require [nordschleife.scenarios :refer :all]
            [clojure.test :refer :all]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]))

(deftest coalesce-acquiesces-tests
  (testing "no adjacent coalesces"
    (are [x] (= (coalesce-acquiesces x) x)
         []
         (take 10 (cycle [:a :b :c :d]))))
  (testing "adjacent coalesces"
    (are [x y] (= (coalesce-acquiesces x) y)
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

(defspec scenarios-dont-have-adjacent-acquiesces
  1000
  (prop/for-all
   [[{group-config :group-config} events] scenario-gen]
   (and
    (not-any? nil? ((juxt :name
                          :cooldown
                          :min-entities
                          :max-entities)
                    group-config))
    (not-any? #(apply (partial = :acquiesce) (map :type %))
              (partition 2 1 events)))))
