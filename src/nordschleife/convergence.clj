(ns nordschleife.convergence
  (:import [org.jclouds.compute.domain.internal NodeMetadataImpl]
           [org.jclouds.compute.domain NodeMetadata]))

(defmacro status-const
  [status]
  `(. org.jclouds.compute.domain.NodeMetadata$Status ~status))

(defn measure-progress
  "Measures progress between states towards the desired state."
  [prev-state curr-state desired-state]
  )
