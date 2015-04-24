(ns nordschleife.auto-scale
  (:require [com.palletops.jclouds.core :refer [module-lookup modules]]
            [clojure.string :refer [capitalize lower-case join split]]
            [clojure.reflect :refer [reflect]]
            [taoensso.timbre :refer [spy]])
  (:import [java.util Properties]
           [org.jclouds ContextBuilder]
           [org.jclouds.rackspace.autoscale.v1 AutoscaleApi]
           [org.jclouds.rackspace.autoscale.v1.domain
            Group
            GroupConfiguration LaunchConfiguration CreateScalingPolicy
            CreateScalingPolicy$ScalingPolicyType
            CreateScalingPolicy$ScalingPolicyTargetType
            LaunchConfiguration$LaunchConfigurationType]
           [org.jclouds.rackspace.autoscale.v1.features GroupApi PolicyApi]))

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

(defn ^:private kw-to-sym
  [kw]
  (let [parts (split (name kw) #"-")
        tail (map capitalize (rest parts))
        as-str (join (cons (first parts) tail))]
    (symbol as-str)))

(defn ^:private sym-to-kw
  [sym]
  (let [parts (re-seq #"[a-z]+(?=[A-Z]|$)|[A-Z][a-z]*" (name sym))]
    (keyword (join "-" (map lower-case parts)))))

(defn ^:private method-names
  [obj]
  (->> (reflect obj)
       :members
       (filter :return-type)
       (map :name)))

(defmacro ^:private builder-helper
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

(defmacro ^:private pojo-wrapper
  [cls maybe-obj]
  `(if (instance? ~cls ~maybe-obj)
     ~maybe-obj
     (builder-helper (. ~cls builder) ~maybe-obj)))

(defn group-config
  "Creates a group configuration.

  Accepts both `GroupConfiguration` objects and maps."
  [gc]
  (pojo-wrapper GroupConfiguration gc))

(defn group-name
  "Gets the name of a group."
  [group]
  (.. group
      (getGroupConfiguration)
      (getName)))

(def LAUNCH_SERVER
  LaunchConfiguration$LaunchConfigurationType/LAUNCH_SERVER)

(defn launch-config
  "Creates a launch configuration.

  Accepts both `LaunchConfiguration` objects and maps. If it is a map,
  LAUNCH_SERVER (the only option) is assumed as the type."
  [lc]
  (pojo-wrapper LaunchConfiguration
                (if (map? lc)
                  (assoc lc :type LAUNCH_SERVER)
                  lc)))

(def DESIRED_CAPACITY
  CreateScalingPolicy$ScalingPolicyTargetType/DESIRED_CAPACITY)

(def INCREMENTAL
  CreateScalingPolicy$ScalingPolicyTargetType/INCREMENTAL)

(def PERCENT_CHANGE
  CreateScalingPolicy$ScalingPolicyTargetType/PERCENT_CHANGE)

(def SCHEDULE
  CreateScalingPolicy$ScalingPolicyType/SCHEDULE)

(def WEBHOOK
  CreateScalingPolicy$ScalingPolicyType/WEBHOOK)

(defn scaling-policy
  "Creates a scaling policy object.

  Note that this doesn't actually create the scaling policy
  upstream. You have to pass the object returned by this function to
  something else; like a call that creates a scaling group, or one
  that adds a scaling policy to an existing scaling group.

  Accepts both `CreateScalingPolicy` objects and maps.

  jClouds uses `CreateScalingPolicy` for scaling policies being
  created; `ScalingPolicy is essentailly the same thing, except with
  added id and links.`"
  [sp]
  (pojo-wrapper CreateScalingPolicy sp))

(defn execute-policy
  "Executes a policy."
  [#^PolicyApi policy-api id]
  (.execute policy-api id))

(defn delete-policy
  "Deletes a policy."
  [#^PolicyApi policy-api id]
  (.delete policy-api id))

(defn get-policies
  "Gets the policies for a group."
  [#^Group group]
  (.getScalingPolicies group))

(defn create-group
  "Create a scaling group."
  [#^GroupApi group-api #^GroupConfiguration group-config'
   #^LaunchConfiguration launch-config' policies]
  (let [group-config' (group-config group-config')
        launch-config' (launch-config launch-config')
        policies (map scaling-policy policies)]
    (.create group-api group-config' launch-config' policies)))

(defn delete-group
  "Deletes a scaling group."
  [#^GroupApi group-api #^Group group]
  (let [group-id (.getId group)]
    (.delete group-api group-id)))
