(ns nordschleife.core
  (:require [nordschleife.services :refer [services]]
            [nordschleife.affect :refer [affect perform-scenarios]]
            [nordschleife.scenarios :refer [sample]]
            [nordschleife.gathering :refer [gather]])
  (:gen-class))

(def the-scenario
  (first (sample 1)))

(def simple-scenario
  [{:group-config
    {:name "nordschleife group ZJQNhRbgsUSt",
     :cooldown 0,
     :min-entities 0,
     :max-entities 10,
     :metadata {}},
    :launch-config
    {:load-balancers [],
     :networks ["11111111-1111-1111-1111-111111111111"],
     :personalities [],
     :server-name "server for nordschleife group ZJQNhRbgsUSt",
     :server-image-ref "0938b7e9-ba56-4af2-a9e6-52c47d931d22",
     :server-flavor-ref "general1-1",
     :server-disk-config "AUTO",
     :server-metadata {}}}
   [{:desired-state {:capacity 1}, :type :scale-to, :amount 1}
    {:type :acquiesce, :desired-state {:capacity 1}}
    {:desired-state {:capacity 2}, :type :scale-up, :amount 1}
    {:desired-state {:capacity 2}, :type :acquiesce}]])

(defn run-1
  []
  (perform-scenarios services [simple-scenario] 10))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
