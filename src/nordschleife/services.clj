(ns nordschleife.services
  (:require [environ.core :refer [env]]
            [com.palletops.jclouds.compute2 :refer :all]))

(def compute
  (let [compute-provider (env :nordschleife-provider)
        creds (map env [:nordschleife-username
                        :nordschleife-api-key])]
    (apply compute-service (conj creds compute-provider))))
