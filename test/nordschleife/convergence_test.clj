(ns nordschleife.convergence-test
  (:require [nordschleife.convergence :refer :all]
            [clojure.test :refer :all])
  (:import [org.jclouds.compute.domain.internal NodeMetadataImpl]))

(def PENDING
  (status-const PENDING))

(def RUNNING
  (status-const RUNNING))

(def ERROR
  (status-const ERROR))

(defn ^:private create-servers
  "Creates some dummy servers for testing."
  [n & {:keys [status base-name]
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
                       hostname)))

(deftest measure-progress-tests
  (testing "reaching convergence"
    (let [prev-state {:servers (create-servers 1)}
          curr-state {:servers (create-servers 2)}
          desired-state {:capacity 2}]
      (is (= (measure-progress prev-state curr-state desired-state)
             {:progress? true :done? true}))))
  (testing "progress while scaling up"
    (let [prev-state {:servers (create-servers 2)}
          curr-state {:servers (create-servers 3)}
          desired-state {:capacity 5}]
      (is (= (measure-progress prev-state curr-state desired-state)
             {:progress? true :amount 1}))))
  (testing "progress while scaling down"
    (let [prev-state {:servers (create-servers 3)}
          curr-state {:servers (create-servers 2)}
          desired-state {:capacity 1}]
      (is (= (measure-progress prev-state curr-state desired-state)
             {:progress? true :amount 1}))))
  (testing "overshoot (group was below desired, and is now above it)"
    (let [prev-state {:servers (create-servers 4)}
          curr-state {:servers (create-servers 6)}
          desired-state {:capacity 5}]
      (is (= (measure-progress prev-state curr-state desired-state)
             {:progress? false :reason "overshoot"}))))
  (testing "undershoot (group was above desired, and is now below it)"
    (let [prev-state {:servers (create-servers 6)}
          curr-state {:servers (create-servers 4)}
          desired-state {:capacity 5}]
      (is (= (measure-progress prev-state curr-state desired-state)
             {:progress? false :reason "undershoot"}))))
  (testing "servers going pending => error"
    (let [working-servers (create-servers 2)
          prev-state {:servers (concat working-servers
                                       (create-servers 2 :status PENDING))}
          curr-state {:servers (concat working-servers
                                       (create-servers 2 :status ERROR))}
          desired-state {:capacity 5}]
      (is (= (measure-progress prev-state curr-state desired-state)
             {:progress? false}))))
  (testing "servers going pending -> error, with some being reaped"
    (let [prev-state {:servers (create-servers 3 :status PENDING)}
          curr-state {:servers (concat (create-servers 1 :status RUNNING)
                                       (create-servers 1 :status ERROR))}
          desired-state {:capacity 5}]
      (is (= (measure-progress prev-state curr-state desired-state)
             {:progress? false}))))
  (testing "servers in error are reaped => no progress"
    (let [prev-state {:servers (concat (create-servers 1 :status RUNNING)
                                       (create-servers 3 :status ERROR))}
          curr-state {:servers (create-servers 1 :status RUNNING)}
          desired-state {:capacity 5}]
      (is (= (measure-progress prev-state curr-state desired-state)
             {:progress? false})))))
