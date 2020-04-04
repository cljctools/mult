(ns mult.main
  (:require
   [clojure.core.async :as a :refer [<! >!  chan go alt! take! put! offer! poll! alts! pub sub
                                     timeout close! to-chan go-loop sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [goog.string :refer [format]]
   [clojure.string :as string]
   [mult.extension :refer [proc-ops]]))

(def vscode (js/require "vscode"))

(def channels (let [system| (chan (sliding-buffer 10))
                    system|pub (pub system| :ch/topic (fn [_] (sliding-buffer 10)))
                    cmd| (chan 10)
                    ops| (chan 10)]
                {:system| system|
                 :system|pub system|pub
                 :cmd| cmd|
                 :ops| ops|}))

(def state (atom {:tabs {:current nil}}))

(def procs (procs-impl
            {:procs {:proc/ops {:proc-fn #'proc-ops
                                :channels #(select-keys % [:cmd| :ops|])
                                :argm #(select-keys % [:state :vscode :context])}}
             :up (fn [procs channels argm]
                   (go
                     (<! (-start procs  :proc/ops))))
             :down (fn [procs channels argm]
                     (go
                       (<! (-stop procs  :proc/ops))))}))

#_(def procs (procs-impl procs-map {:channels channels
                                    :argm {:state state
                                           :vscode vscode
                                           :context context}}))

(defn activate
  [context]
  (when-not (not (-up? procs))
    (-up procs {:channels channels
                :argm {:state state
                       :vscode vscode
                       :context context}}))
  (put! (channels :ops|) {:op :activate}))

(defn deactivate []
  (go
    (let [c| (chan 1)]
      (>! (channels :ops|) {:op :deactivate :c| c|})
      (<! c|)
      (when (-up? procs)
        (-down procs)))))

(defn reload
  []
  (.log js/console "Reloading...")
  (js-delete js/require.cache (js/require.resolve "./main")))

(def exports #js {:activate activate
                  :deactivate deactivate})