(ns mult.impl.conn
  (:require
   [clojure.core.async :as a :refer [chan go go-loop <! >!  take! put! offer! poll! alt! alts! close!
                                     pub sub unsub mult tap untap mix admix unmix
                                     timeout to-chan  sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [goog.string :refer [format]]
   [clojure.string :as string]
   [cljs.reader :refer [read-string]]
   [mult.protocols.conn :as p.conn]))


(defn nrepl-basic
  [{:keys [url out|]}]
  (let []
    (reify
      p.conn/Conn
      (-disconnect [_])
      (-connect [_]
        
        )
      (-eval [_ code]
        
        ))))