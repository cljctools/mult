(ns mult.impl.core
  (:require
   [clojure.core.async :as a :refer [<! >!  chan go alt! take! put! offer! poll! alts! pub sub
                                     timeout close! to-chan go-loop sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [goog.string :refer [format]]
   [clojure.string :as string]
   [clojure.pprint :refer [pprint]]
   ["fs" :as fs]
   ["path" :as path]
   ["net" :as net]
   ["bencode" :as bencode]
   [cljs.reader :refer [read-string]]
   [bencode-cljc.core :refer [serialize deserialize]]
   [mult.protocol :refer [Ops| Tab Connection]]))

(defn ops|-interface
  []
  (let []
    (reify Ops|
      (-op-tab-add [_] :tab/add)
      (-op-tab-on-dispose [_] :tab/on-dispose)
      (-tab-add [_ v])
      (-tab-on-dispose [_ id]
        {:op :tab/on-dispose :tab/id id})
      (-tab-send [_ v]
        {:op :tab/send
         :tab/id :current
         :tab/msg {:op :tabapp/inc}}))))

(defn pret [x]
  (binding [*out* *out* #_*err*]
    (pprint x))
  x)

(defn explain [result comment & data]
  (pprint comment)
  {:result result
   :comment comment
   :data data})


