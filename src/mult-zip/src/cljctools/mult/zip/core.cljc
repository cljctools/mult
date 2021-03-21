(ns cljctools.mult.zip.core
  (:require
   [clojure.string]
   [clojure.pprint :refer [pprint]]
   #?(:cljs [cljs.reader :refer [read-string]])
   #?(:cljs [goog.string.format])
   #?(:cljs [goog.string :refer [format]])
   [clojure.spec.alpha :as s]

   [clojure.walk]

   [rewrite-clj.zip :as z]
   [rewrite-clj.parser :as p]
   [rewrite-clj.node :as n]
   [rewrite-clj.paredit]))

(defn read-ns-symbol
  "Read the namespace name from a string (beggining of text file).
   At least the (ns ..) form should be par tof string.
   String can be invalid, as long as ns form is valid. 
   Takes the second form which is namespace symbol"
  [string]

  (let [node (p/parse-string string)
        zloc (z/of-string (n/string node))
        ns-symbol (-> zloc z/down z/right z/sexpr)]
    ns-symbol))