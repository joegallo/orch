(ns orch.api-test
  (:refer-clojure :exclude [get reset!])
  (:require [clojure.test :refer :all]
            [orch.api :refer :all]))

(use-fixtures :each
  (fn [f]
    (delete! :foo :force)
    (f)))

(deftest api-url-test
  (is (= api-root (api-url)))
  (is (= "https://api.orchestrate.io/v0/a/1/b/2" (api-url :a 1 "b" 2))))

(deftest etag->ref-test
  (is (= "foo"
         (etag->ref "\"foo\"")
         (etag->ref "\"foo-gzip\""))))

(deftest etag->ref-test
  (is (= "\"foo\"" (ref->etag "foo")))
  (is (= "\"*\"" (ref->etag "*"))))

(deftest ping-test
  (is (ping)))

(deftest get-test
  (let [o1 (put! :foo 1 {:foo "bar" :count 1})
        o2 (put! :foo 1 {:foo "bar" :count 2})]
    (testing "get returns the most recently put! value"
      (is (= o2 (get :foo 1))))
    (testing "put! returns a value with metadata"
      (is (= {:collection :foo :key 1}
             (dissoc (meta o1) :ref)
             (dissoc (meta o2) :ref))))
    (testing "that can be used to see individual refs"
      (is (= o1 (get :foo 1 :refs (:ref (meta o1)))))
      (is (= o2 (get :foo 1 :refs (:ref (meta o2))))))))

(deftest put!-test
  ;; see get-test for the simple case
  (let [o3 (put! :foo 1 {:x 3})
        o4 (put! :foo 1 {:x 4})]
    (testing "put! accepts a ref argument for conditional puts"
      ;; we can specify whether to create only if no value is already there
      (is (nil? (put! :foo 1 :none {:count 2})))
      (is (= o4 (get :foo 1))) ;; no change, it's still o4
      ;; or only if the expected ref is current
      (is (nil? (put! :foo 1 (:ref (meta o3)) {:count 2})))
      (is (= o4 (get :foo 1))) ;; no change, it's still o4
      ;; it will change if the provided ref is the current ref
      (let [o5 (put! :foo 1 (:ref (meta o4)) {:count 3})]
        (is (= o5 (get :foo 1))))))) ;; changed!
