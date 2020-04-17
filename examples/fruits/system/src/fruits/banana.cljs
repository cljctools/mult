(ns fruits.banana
  (:require
   [clojure.core.async :as async :refer [<! >!  chan go alt! take! put!  alts! pub sub]]
   [fruits.impl.core :as core]))

(defn ^:export main
  []
  (println "; main"))

(comment

  (core/foo)

  (+ 1 2)

  (shadow.cljs.devtools.api/watch :app)

  (shadow.cljs.devtools.api/nrepl-select :app)

  

  ;;
  )

