(ns file-backed-cache.core
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [hasch.core :as hash])
  (:import [clojure.lang IDeref]
           [java.io PushbackReader]
           [java.lang.ref Cleaner]
           [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(defonce store-location
  (Files/createTempDirectory "clj-immut" (make-array FileAttribute 0)))

(defprotocol IStored
  (store [v] "Persist Clojure value `v` to disk."))

(defonce ^:private cleaner (Cleaner/create))

(defrecord Persisted [file]
  IDeref
  (deref [_] (-> file io/reader PushbackReader. edn/read)))
(defmethod pp/simple-dispatch Persisted [{:keys [file]}] (pr file))

(defn persist
  [v]
  (let [f         (io/file store-location (str (hash/uuid v)))
        persisted (->Persisted f)]
    (with-open [w (io/writer f)]
      (binding [*out* w
                ;; TODO: figure out why *print-length* is bound to 100 in my REPL.
                *print-length* nil]
        (pr v)))
    (.register cleaner persisted #(do (println "GCing" f) (io/delete-file f)))
    persisted))
