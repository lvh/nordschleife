(ns nordschleife.services
  (:require [environ.core :refer [env]]
            [com.palletops.jclouds.compute2 :refer :all]
            [nordschleife.auto-scale :refer [auto-scale-api]]))

(def services
  (let [compute-provider (env :nordschleife-provider)
        creds (map env [:nordschleife-username
                        :nordschleife-api-key])]
    {:compute (apply compute-service (conj creds compute-provider))
     :auto-scale (apply auto-scale-api creds)}))
