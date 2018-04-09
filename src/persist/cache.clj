(ns persist.cache
  (:require [clojure.core.cache :as c]
            [persist.core :as p]))

(c/defcache PersistedCache [cache]
  c/CacheProtocol
  (lookup [_ item] (get cache item))
  (lookup [_ item not-found] (get cache item not-found))
  (has? [_ item] (contains? cache item))
  (hit [this _] this)
  (miss [_ item result] (PersistedCache. (assoc cache item (p/persist result))))
  (evict [_ key] (PersistedCache. (dissoc cache key)))
  (seed [_ base]
        (PersistedCache.
         (into {}
               (map (juxt key (comp p/persist val)))
               base))))

(defn persisted-cache-factory
  "Yield a persisted cache initialized to `base`."
  [base]
  {:pre [(map? base)]}
  (c/seed (PersistedCache. {}) base))
