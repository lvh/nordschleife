(ns nordschleife.convergence-test
  (:require [nordschleife.convergence :refer :all]
            [clojure.set :as set]
            [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop])
  (:import [org.jclouds.compute.domain.internal NodeMetadataImpl]))

(defmacro status-const
  [status]
  `(. org.jclouds.compute.domain.NodeMetadata$Status ~status))

(def PENDING
  (status-const PENDING))

(def RUNNING
  (status-const RUNNING))

(def ERROR
  (status-const ERROR))

(defn ^:private create-servers
  "Creates some dummy servers for testing."
  ([n]
   (create-servers n nil))
  ([n {:keys [status base-name]
       :or {status RUNNING
            base-name "nordschleife"}}]
   (for [i (range n)
         :let [name (str base-name "-" i)
               provider-id base-name
               name name
               id (str i)
               location nil
               uri nil
               user-metadata {}
               tags #{}
               group nil
               hardware nil
               image-id nil
               os nil
               backend-status nil
               login-port 1234
               public-addrs ["1.2.3.4"]
               private-addrs ["10.11.12.13"]
               credentials nil
               hostname name]]
     (NodeMetadataImpl. provider-id name id location uri user-metadata tags
                        group hardware image-id os status backend-status
                        login-port public-addrs private-addrs credentials
                        hostname))))

(defn build-state
  ([n]
   (build-state n nil))
  ([n server-args]
   {:servers (create-servers n server-args)}))

(defn merge-states
  [& ss]
  (apply merge-with concat ss))

(deftest filter-group-tests
  (let [create (fn [group-name]
                 (let [base-name (str "server for " group-name)]
                   (mapcat (fn [status]
                             (create-servers 5
                                             {:base-name base-name
                                              :status status}))
                           [RUNNING PENDING ERROR])))
        [xyzzy-servers iddqd-servers] (map create ["xyzzy" "iddqd"])
        all-servers (concat xyzzy-servers iddqd-servers)]
    (is (= (filter-group "xyzzy" {:servers all-servers})
           {:servers xyzzy-servers}))
    (is (= (filter-group "iddqd" {:servers all-servers})
           {:servers iddqd-servers}))))

(deftest pick-victims-tests
  (let [group-name "xyzzy"
        servers (create-servers 5 {:base-name group-name})
        to-kill (pick-victims group-name {:servers servers} 3)]
    (is (set/subset? (set to-kill) (set servers)))))

(deftest measure-progress-tests
  (testing "reaching convergence"
    (let [prev-state (build-state 2)
          curr-state (build-state 2)
          desired-state {:capacity 2}]
      (is (= (measure-progress prev-state curr-state desired-state)
             {:progress? true :done? true}))))
  (testing "progress while scaling up"
    (let [prev-state (build-state 2)
          curr-state (build-state 3)
          desired-state {:capacity 5}]
      (is (= (measure-progress prev-state curr-state desired-state)
             {:progress? true :amount 1}))))
  (testing "progress while scaling down"
    (let [prev-state (build-state 3)
          curr-state (build-state 2)
          desired-state {:capacity 1}]
      (is (= (measure-progress prev-state curr-state desired-state)
             {:progress? true :amount 1}))))
  (testing "overshoot (group was below desired, and is now above it)"
    (let [prev-state (build-state 4)
          curr-state (build-state 6)
          desired-state {:capacity 5}]
      (is (= (measure-progress prev-state curr-state desired-state)
             {:progress? false :reason "overshoot"}))))
  (testing "undershoot (group was above desired, and is now below it)"
    (let [prev-state (build-state 6)
          curr-state (build-state 4)
          desired-state {:capacity 5}]
      (is (= (measure-progress prev-state curr-state desired-state)
             {:progress? false :reason "undershoot"}))))
  (testing "servers going pending => error"
    (let [working-state (build-state 2 {:status RUNNING})
          prev-state (merge-states working-state
                                   (build-state 2 {:status PENDING}))
          curr-state (merge-states working-state
                                   (build-state 2 {:status ERROR}))
          desired-state {:capacity 5}]
      (is (= (measure-progress prev-state curr-state desired-state)
             {:progress? false}))))
  (testing "servers going pending -> error, with some being reaped"
    (let [prev-state (build-state 3 {:status PENDING})
          curr-state (merge-states (build-state 1 {:status RUNNING})
                                   (build-state 1 {:status ERROR}))
          desired-state {:capacity 5}]
      (is (= (measure-progress prev-state curr-state desired-state)
             {:progress? false}))))
  (testing "servers in error are reaped => no progress"
    (let [prev-state (merge-states (build-state 1 {:status RUNNING})
                                   (build-state 3 {:status ERROR}))
          curr-state (build-state 1 {:status RUNNING})
          desired-state {:capacity 5}]
      (is (= (measure-progress prev-state curr-state desired-state)
             {:progress? false})))))
