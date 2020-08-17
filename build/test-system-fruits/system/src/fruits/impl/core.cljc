(ns fruits.impl.core
  (:require
   [clojure.string :as str]
   [fruits.protocols :as p]))

(defn foo
  "Returns 'bar"
  {:arglists '([] [val])}
  ([] 'bar)
  ([x] ['bar x]))

(comment

  (foo)

  ;;
  )