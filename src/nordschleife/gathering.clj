(ns nordschleife.gathering
  "Gather information about the state of the cloud."
  (:require [com.palletops.jclouds.compute2 :refer [nodes]])
  (:import [java.util Date]))

(defn gather
  "Gather all of the available information."
  [services]
  {:timestamp (Date.)
   :servers (nodes (:compute services))})
