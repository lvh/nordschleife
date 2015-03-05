(ns nordschleife.gathering
  "Gather information about the state of the cloud."
  (:require [environ.core :refer [env]]
            [com.palletops.jclouds.compute2 :refer :all]))

(defonce compute
  (apply compute-service (map env [:nordschleife-provider
                                   :nordschleife-username
                                   :nordschleife-api-key])))

(defn ^:private gather-servers
  "Gather all available servers."
  [compute]
  (nodes compute))

(defn gather
  "Gather all of the available information."
  [compute]
  {:servers (gather-servers compute)})
