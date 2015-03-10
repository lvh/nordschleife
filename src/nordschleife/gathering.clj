(ns nordschleife.gathering
  "Gather information about the state of the cloud."
  (:require [com.palletops.jclouds.compute2 :refer [nodes]]))

(defn gather
  "Gather all of the available information."
  [services]
  {:servers (nodes (:compute services))})
