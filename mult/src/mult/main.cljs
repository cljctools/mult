(ns mult.main
  (:require
   [clojure.core.async :as a :refer [<! >!  chan go alt! take! put! offer! poll! alts! pub sub
                                     timeout close! to-chan go-loop sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [goog.string :refer [format]]
   [clojure.string :as string]
   [mult.extension :refer [proc-ops-impl proc-log-impl]]))

(def vscode (js/require "vscode"))

(def channels (let [system| (chan (sliding-buffer 10))
                    system|pub (pub system| :ch/topic (fn [_] (sliding-buffer 10)))
                    cmd| (chan 10)
                    cmd|m (mult cmd|)
                    ops| (chan 10)
                    ops|m (mult ops|)
                    log| (chan 100)
                    log|m (mult log|)]
                {:system| system|
                 :system|pub system|pub
                 :cmd| cmd|
                 :cmd|m cmd|m
                 :ops| ops|
                 :ops|m ops|m
                 :log| log|
                 :log|m log|m}))

(def procs (procs-impl
            {:procs {:proc-ops {:proc-fn #'proc-ops-impl
                                :ctx-fn #(-> % (select-keys [:channels  :vscode :vscode-context])
                                             (update :channels #(select-keys % [:cmd| :ops|])))}
                     :proc-log {:proc-fn #'proc-log-impl
                                :ctx-fn #(-> % (select-keys [:channels  :vscode :vscode-context])
                                             (update :channels #(select-keys % [:cmd| :ops|])))}}
             :up (fn [ctx procs]
                   (go
                     (->> [(-start procs  :proc-ops)]
                          (a/map vector)
                          (<!))
                     (<! (-start procs  :proc-log procs))))
             :down (fn [ctx procs]
                     (go
                       (<! (-stop procs  :proc-log))))}))

#_(def procs (procs-impl procs-map {:channels channels
                                    :vscode vscode
                                    :context context}))

(defn activate
  [context]
  (when-not (not (-up? procs))
    (-up procs {:channels channels
                :vscode vscode
                :vscode-context context}))
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