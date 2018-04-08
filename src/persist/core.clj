(ns persist.core
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

(defonce ^:private cleaner (Cleaner/create))

;; reference counts
(defonce ^:private ^{:doc "Reference counts to items in the store."} store-agent
  (agent {}))

(defrecord Persisted [file]
  IDeref
  (deref [_] (-> file io/reader PushbackReader. edn/read)))
(defmethod pp/simple-dispatch Persisted [{:keys [file]}] (pr file))
(defmethod print-method Persisted [p ^java.io.Writer w]
  (.write w (str p)))

(defn persist
  [v]
  (let [file-name (-> v hash/uuid str)
        f         (io/file store-location file-name)
        persisted (->Persisted f)]
    (send-off store-agent
      (fn [counts]
        (if (counts file-name)
          (update counts file-name inc)
          (do
            (with-open [w (io/writer f)]
              (binding [*out* w
                        ;; TODO: figure out why *print-length* is
                        ;; bound to 100 in my REPL.
                        *print-length* nil]
                (pr v)))
            (assoc counts file-name 1)))))
    (.register cleaner persisted
      #(send-off store-agent
         (fn [counts]
           (let [count (-> file-name counts dec)]
             (if (zero? count)
               (do
                 (println "GCing" f)
                 (io/delete-file f)
                 (dissoc counts file-name))
               (assoc counts file-name count))))))
    (await store-agent)
    persisted))
