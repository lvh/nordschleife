(ns nordschleife.auto-scale
  (:require [com.palletops.jclouds.core :refer [define-accessors dashed make-option-map module-lookup modules]])
  (:import [java.util Properties]
           [org.jclouds ContextBuilder]
           [org.jclouds.rackspace.autoscale.v1 AutoscaleApi]
           [org.jclouds.rackspace.autoscale.v1.domain GroupConfiguration]))

(defn auto-scale-api
  [#^String username #^String api-key & options]
  (let [provider "rackspace-autoscale-us"
        module-keys (set (keys module-lookup))
        ext-modules (filter #(module-keys %) options)
        opts (apply hash-map (filter #(not (module-keys %)) options))]
    (.. (ContextBuilder/newBuilder provider)
        (credentials username api-key)
        (modules (apply modules (concat ext-modules (opts :extensions))))
        (overrides (reduce #(do (.put %1 (name (first %2)) (second %2)) %1)
                           (Properties.) (dissoc opts :extensions)))
        (buildApi AutoscaleApi))))

(defn group-api
  [#^AutoscaleApi auto-scale-api #^String zone]
  (.getGroupApiForZone auto-scale-api zone))

(defn policy-api
  [#^AutoscaleApi auto-scale-api #^String zone #^String group-id]
  (.getPolicyApiForZoneAndGroup auto-scale-api zone group-id))

(defn webhook-api
  [#^AutoscaleApi auto-scale-api #^String zone #^String group-id]
  (.getPolicyApiForZoneAndGroup auto-scale-api zone group-id))

(defn create
  "Create a scaling group."
  []
  )
(defn kw-to-sym
  [kw]
  (let [parts (split (name kw) #"-")
        tail (map capitalize (rest parts))
        as-str (join (cons (first parts) tail))]
    (symbol as-str)))

(defmacro builder-helper
  [builder keys map]
  (let [method-forms (for [k keys
                           :let [m (kw-to-sym k)]]
                       `((fn [builder#]
                           (if-let [v# (~k ~map)]
                             (. builder# ~m v#)
                             builder#))))]
    `(->> (~builder)
          ~@method-forms
          (.build))))

(defn group-config
  "Creates a group configuration.

  Accepts both `GroupConfiguration` objects and maps."
  [gc]
  (if (instance? GroupConfiguration gc)
    gc
    (builder-helper GroupConfiguration/builder
                    [:name :cooldown :min-entities :max-entities :metadata]
                    gc)))
        (build))))
