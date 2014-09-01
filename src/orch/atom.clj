(ns orch.atom
  "this is just a little tech demo that i created while playing around
  with orchestrate.io and thinking about ted dziuba's thoughts on
  REVAT (http://teddziuba.github.io/2014/08/18/the-s-in-rest/) -- this
  is obviously not a fully-featured client, it's just a toy"
  (:refer-clojure :exclude [atom swap! reset!])
  (:require [orch.api :as o]))

(defrecord OAtom [collection key]
  clojure.lang.IDeref
  (deref [_]
    (o/get collection key)))

;; blergh... this was required to make oatoms print nicely at the repl
;; rather than throwing an exception
(defmethod print-method OAtom [o ^java.io.Writer w]
  (.write w (str (format "#<%s@%x: "
                         (.getSimpleName (class o))
                         (System/identityHashCode o))
                 (pr-str @o) ">")))

(defn atom
  "oatom constructor"
  [coll key]
  (->OAtom coll key))

(defn swap!
  "like clojure.core/swap!, but for oatoms"
  [oatom f & args]
  (loop []
    (let [curr @oatom
          newval (apply f curr args)]
      (if-let [ret (o/put! (:collection oatom)
                           (:key oatom)
                           (:ref (meta curr))
                           newval)]
        ;; it worked, return the new newval
        ret
        ;; it failed, try again
        (recur)))))

(defn reset!
  "like clojure.core/reset!, but for oatoms"
  [oatom newval]
  (o/put! (:collection oatom)
          (:key oatom)
          newval))
