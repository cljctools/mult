(ns fruits.impl.render
  (:require
   [clojure.string :as str]
   [fruits.protocols :as p]))

(defn bar
  "Returns 'foo"
  {:arglists '([] [val])}
  ([] 'foo)
  ([x] ['foo x]))

(comment

  (bar)
  (+ 1 2)

  ;;
  )