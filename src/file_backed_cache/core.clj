(ns file-backed-cache.core
  (:require [clojure.core.cache :as cache :refer [defcache]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.test :refer :all]
            [hasch.core :as hash])
  (:import [clojure.lang IDeref]
           [java.io PushbackReader]
           ;; TODO: use JDK 9.
           #_[java.lang.ref Cleaner]
           [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(defn directory->map
  [d]
  (into
    {}
    (map (fn [f] [(.getName f) (-> f io/reader PushbackReader. edn/read)]))
    (-> d io/as-file file-seq)))

(defn map->directory
  [d m]
  (map (fn [[k v]] (spit (io/file d k)) v)) m)
;;TODO: possibly delete above.

(def store-location
  (Files/createTempDirectory "clj-immut" (make-array FileAttribute 0)))

(defprotocol IStored
  (store [v] "Persist Clojure value `v` to disk."))

(defn directly-store
  [x]
  (spit (io/file store-location (hash/uuid x)) x))

;; TODO: decide which I want.

;; (1) atom-semantics. Derefing fetches from disk, so objects aren't
;; held in the JVM. Swapping in a new value has the additional effect
;; of deleting files that aren't referenced from disk.

;; (2) JVM object semantics. Use https://stackoverflow.com/a/158216 to
;; delete from disk at cleanup time. But an object will remain in mem
;; until it's GC'ed, so what was the point of keeping the on-disk
;; copy?

;; (3) Cache semantics. CacheProtocol's evict deletes from disk. But
;; to prevent a cached thing from sitting in mem, we would have to
;; /not/ propagate to the subordinate cache.

;; (1) seems hard. (2) seemed cool at first but now seems kind of
;; pointless. (3) might work.

(defrecord Persisted [file]
  IDeref
  (deref [_]
    (let [v (-> file io/reader PushbackReader. edn/read)]
      (io/delete-file file)
      v)))
(defmethod pp/simple-dispatch Persisted [{:keys [file]}] (pr file))

(defn persist
  [v]
  (let [f (io/file store-location (str (hash/uuid v)))]
    (pr spit f v)
    (->Persisted f)))

;; Not only does this test fail (likely due to confounding variables),
;; it also made me realize that because the file is never
;; dereferenced, it is never deleted -- it is effectively leaked.
(deftest mem-usage
  (let [mem-0 (.totalMemory (Runtime/getRuntime))
        large-object-holder (->> rand
                                 repeatedly
                                 (take (* 1024 1024 4))
                                 vec
                                 atom)
        mem-1 (.totalMemory (Runtime/getRuntime))
        _ (swap! large-object-holder file-backed-cache.core/persist)
        _ (System/gc)
        mem-2 (.totalMemory (Runtime/getRuntime))]
    (is (< mem-0 mem-1))
    (is (< mem-2 mem-1))))

#_
(defcache FileCache [cache ks directory]
  cache/CacheProtocol
  (lookup [_ item] (get cache item))
  (lookup [_ item not-found] (get cache not-found))
  (has? [_ item] (contains? cache item))
  (hit [this _] this)
  (miss [_ item result]
    (FileCache. ))
  (evict [_ key]
    (io/delete-file (io/file directory key))
    (FileCache.
      (dissoc cache key)
      (dissoc ks key)
      ;; FIXME: I need a store directory because reusing `directory`
      ;; does not match immutable semantics.
      directory)))

;; Represent the OR of two caches, complimenting the default
;; compositional semantics (which is like the AND of two caches)
#_
(defcache FallbackCache [left right])
