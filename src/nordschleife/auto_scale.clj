(ns nordschleife.auto-scale
  (:require [com.palletops.jclouds.core :refer [define-accessors dashed make-option-map module-lookup modules]]
            [clojure.string :refer [capitalize lower-case join split]]
            [clojure.reflect :refer [reflect]]
            [taoensso.timbre :refer [spy]])
  (:import [java.util Properties]
           [org.jclouds ContextBuilder]
           [org.jclouds.rackspace.autoscale.v1 AutoscaleApi]
           [org.jclouds.rackspace.autoscale.v1.domain GroupConfiguration LaunchConfiguration]))

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

(defn sym-to-kw
  [sym]
  (let [parts (re-seq #"[a-z]+(?=[A-Z]|$)|[A-Z][a-z]*" (name sym))]
    (keyword (join "-" (map lower-case parts)))))

(defn method-names
  [obj]
  (->> (reflect obj)
       :members
       (filter :return-type)
       (map :name)))

(defmacro builder-helper
  [builder-expr map]
  (let [builder (eval builder-expr)
        method-names (remove (partial = 'build)
                             (method-names builder))
        method-forms (for [m method-names
                           :let [k (sym-to-kw m)]]
                       `((fn [builder#]
                           (if-let [v# (~k ~map)]
                             (. builder# ~m v#)
                             builder#))))]
    `(->> ~builder-expr
          ~@method-forms
          (.build))))

(defn group-config
  "Creates a group configuration.

  Accepts both `GroupConfiguration` objects and maps."
  [gc]
  (if (instance? GroupConfiguration gc)
    gc
    (builder-helper (GroupConfiguration/builder) gc)))
