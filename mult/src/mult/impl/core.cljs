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
   [mult.protocols.core :refer [Ops| Tab Connection]]))


(defn pret [x]
  (binding [*out* *out* #_*err*]
    (pprint x))
  x)

(defn explain [result comment & data]
  (pprint comment)
  {:result result
   :comment comment
   :data data})


