(ns cljctools.mult.impl
  (:require
   [clojure.core.async :as a :refer [chan go go-loop <! >! take! put! offer! poll! alt! alts! close!
                                     pub sub unsub mult tap untap mix admix unmix pipe
                                     timeout to-chan  sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [clojure.string]
   [clojure.pprint :refer [pprint]]
   #?(:cljs [cljs.reader :refer [read-string]])
   #?(:cljs [goog.string.format])
   #?(:cljs [goog.string :refer [format]])
   [clojure.spec.alpha :as s]

   [sci.core :as sci]
   [cljctools.edit.core :as edit.core]

   [cljctools.mult.spec :as mult.spec]
   [cljctools.mult.protocols :as mult.protocols]))

(defn send-data
  [tab data]
  {:pre [(s/assert ::mult.spec/mult-ops (:op data))]}
  (mult.protocols/send* tab (pr-str data)))

(defn filepath-to-nrepl-ids
  [config filepath]
  (let [opts {:namespaces {'foo.bar {'x 1}}}
        sci-ctx (sci/init opts)]
    (into []
          (comp
           (filter (fn [{:keys [::mult.spec/nrepl-id
                                ::mult.spec/include-file?]}]
                     (let [include-file?-fn (sci/eval-string* sci-ctx (pr-str include-file?))]
                       (include-file?-fn filepath))))
           (map ::mult.spec/nrepl-id))
          (::mult.spec/nrepl-metas config))))