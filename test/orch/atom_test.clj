(ns orch.atom-test
  (:require [clojure.test :refer :all]
            [orch.atom :as o]
            [orch.api :refer [delete!]]))

(use-fixtures :each
  (fn [f]
    (delete! :foo :force)
    (delete! :bar :force)
    (f)))

(deftest atom-test
  (let [a1 (o/atom :foo 1)
        a2 (o/atom :foo 1)]
    (o/reset! a1 {:foo 123})
    (is (= {:foo 123} @a1 @a2))))

(deftest swap-test
  (let [a1 (o/atom :bar 1)
        a2 (o/atom :bar 1)
        _ (o/reset! a1 {:count 1})
        d1 (delay (o/swap! a1 (fn [m]
                                (Thread/sleep 1000)
                                (-> m
                                    (update-in [:count] inc)
                                    (assoc :d1 true)))))
        d2 (delay (o/swap! a2 (fn [m]
                                (Thread/sleep 500)
                                (-> m
                                    (update-in [:count] inc)
                                    (assoc :d2 true)))))]
    (testing "swap! allows for multithreaded atomic updates"
      (doall (pmap deref [d1 d2]))
      (is (= {:count 3 :d1 true :d2 true} @a1 @a2)))))

;; no need for a reset-test, it's exercised by the previous tests
