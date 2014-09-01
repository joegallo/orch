(ns orch.api
  "this is just a little tech demo that i created while playing around
  with orchestrate.io and thinking about ted dziuba's thoughts on
  REVAT (http://teddziuba.github.io/2014/08/18/the-s-in-rest/) -- this
  is obviously not a fully-featured client, it's just a toy"
  (:refer-clojure :exclude [get reset!])
  (:require [clojure.string :as string]
            [clj-http.client :as http]
            [slingshot.slingshot :refer [try+]]))

;; obviously i'm not going to share my api-key, so i've got it in
;; resources/pass.clj and i'm picking it up from there. that file is
;; .gitignored. a real api for orchestrate would probably have you
;; passing that in via some sort of config object.
(declare api-key)
(try
  (load "/pass")
  (catch Exception e))

(def api-root "https://api.orchestrate.io/v0")

(defn api-url
  "constructor for orchestrate urls, it will turn keywords into
  strings and separate components with slashes"
  [& strs]
  (->> strs
       (cons api-root)
       (map (fn [s] (if (keyword? s)
                      (name s)
                      s)))
       (string/join "/")))

(defn etag->ref
  "etags come back like \"\"foo\"\" or \"\"foo-gzip\"\" (that is, a
  string with double-quotes inside, and potentially a -gzip, too --
  strip out the extra stuff so we end up with just \"foo\""
  [s]
  (.replaceFirst s "^\"(.*?)(-gzip)?\"$" "$1"))

(defn ref->etag
  "surrounds a ref in double quotes"
  [s]
  (str "\"" s "\""))

(defn ping
  "check if the service is up, just a sanity check"
  []
  (= 200 (:status (http/head (api-url)
                             {:basic-auth api-key}))))

(defn get
  "get the current value of a object, or the value for a specific ref
  of that object "
  {:arglists '[[coll key] [coll key :refs ref]]}
  [coll key & args]
  (try+
   (let [{:keys [headers body] :as r} (http/get (apply api-url coll key args)
                                                {:basic-auth api-key
                                                 :as :json})
         ref (etag->ref (:etag headers))]
     (with-meta body
       {:collection coll
        :key key
        :ref ref}))
   (catch [:status 404] []
     ;; it doesn't exist
     nil)))

(defn put!
  "put a new value for an object, potentially comparing against an
  existing value before doing so"
  ([coll key newval]
     (put! coll key nil newval))
  ([coll key oldref newval]
     (try+
      (let [headers (when oldref
                      (if (= oldref :none)
                        {:if-none-match (ref->etag "*")}
                        {:if-match (ref->etag oldref)}))
            {:keys [headers]} (http/put (api-url coll key)
                                        (merge {:basic-auth api-key
                                                :content-type :json
                                                :form-params newval
                                                :headers headers}))
            ref (etag->ref (:etag headers))]
        (with-meta newval
          {:collection coll
           :key key
           :ref ref}))
      (catch [:status 412] []
        ;; ignorable: you none for oldref and there was already a
        ;; value, or you specified a ref but it didn't match
        nil))))

(defn delete!
  "delete an entire collection or just an individual object within a collection"
  {:arglists '[[coll force?]
               [coll key]
               [coll key purge?]]}
  [coll & args]
  ;; eh, this is a little gross, but the delete operation is a little
  ;; complicated since it can serve for entire collections or
  ;; individual objects
  (let [[key param] (map first ((juxt remove filter) keyword? args))]
    (http/delete (if key
                   (api-url coll key)
                   (api-url coll))
                 (merge {:basic-auth api-key}
                        (if param
                          {:query-params {param true}}))))
  nil)
