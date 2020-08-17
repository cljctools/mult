(ns fruits.mango
  (:require
   [clojure.core.async :as async :refer [<! >!  chan go alt! take! put!  alts! pub sub]]
   [dev.nrepl :as nrepl]
   [fruits.impl.core :as core]))


(defn -main [& args]
  (nrepl/nserver-clj {:host "0.0.0.0" :port 7788}))

(comment

  (core/foo)

  (+ 3 2)

  ;;
  )

