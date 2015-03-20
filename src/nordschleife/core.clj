(ns nordschleife.core
  (:require [nordschleife.services :refer [services]]
            [nordschleife.affect :refer [affect perform-scenarios]]
            [nordschleife.scenarios :refer [sample]]
            [nordschleife.gathering :refer [gather]])
  (:gen-class))

(def the-scenario
  (first (sample 1)))

(defn run-1
  []
  (perform-scenarios services [the-scenario] 10))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
