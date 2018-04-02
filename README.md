# persist

Persist Clojure data structures to the file system. Derefence them to
bring them back.

Clojure data structures are serializable "for free". Thus, there's no
need to keep large data structures in memory when they could be
written to disk.

## Usage

```clojure
(def persisted-value (persist.core/persist my-val))

;; Allow my-val to become eligible for garbage collection.

@persisted-value
;; reads the value out of disk and into memory.
```

## License

Copyright © 2018 Alex ter Weele

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
