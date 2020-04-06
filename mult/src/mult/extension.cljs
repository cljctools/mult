(ns mult.extension
  (:require
   [clojure.core.async :as a :refer [<! >!  chan go alt! take! put! offer! poll! alts! pub sub
                                     timeout close! to-chan go-loop sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [goog.string :refer [format]]
   [clojure.string :as string]
   [cljs.reader :refer [read-string]]
   [mult.protocols.core]
   [mult.protocols.editor]
   [mult.protocols.proc]
   [mult.impl.proc :refer [procs-impl proc-log]]
   [mult.impl.editor :refer [proc-editor]]))

(def channels (let [system| (chan (sliding-buffer 10))
                    system|pub (pub system| :ch/topic (fn [_] (sliding-buffer 10)))
                    cmd| (chan 10)
                    cmd|m (mult cmd|)
                    ops| (chan 10)
                    ops|m (mult ops|)
                    log| (chan 100)
                    log|m (mult log|)
                    editor| (chan 10)
                    editor|m (mult editor|)
                    editor|p (pub (tap editor|m (chan 10)) :topic (fn [_] 10))]
                {:system| system|
                 :system|pub system|pub
                 :cmd| cmd|
                 :cmd|m cmd|m
                 :ops| ops|
                 :ops|m ops|m
                 :log| log|
                 :log|m log|m
                 :editor| editor|
                 :editor|m editor|m
                 :editor|p editor|p}))

(declare proc-ops)

(def procs (procs-impl
            {:procs {:proc-ops {:proc-fn #'proc-ops
                                :ctx-fn identity
                                #_(fn [ctx]
                                    (-> % (select-keys [:channels  :editor-ctx])
                                        (update :channels #(select-keys % [:cmd| :ops|]))))}
                     :proc-log {:proc-fn #'proc-log
                                :ctx-fn identity}
                     :proc-editor {:proc-fn #'proc-editor
                                   :ctx-fn identity}}
             :up (fn [ctx procs]
                   (go
                     (->> [(-start procs  :proc-ops)]
                          (a/map vector)
                          (<!))
                     (<! (-start procs  :proc-log procs))))
             :down (fn [ctx procs]
                     (go
                       (<! (-stop procs  :proc-log))))}))

(defn activate
  [context]
  (when-not (not (-up? procs))
    #_(-up procs {:channels channels
                  :editor-ctx context}))
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

(defn default-commands
  []
  ["mult.activate"
   "mult.ping"
   "mult.deactivate"
   "mult.counter"])

(defn proc-ops
  [{channels :channels}]
  (let [{:keys [proc| log| editor| editor|p]} channels
        editor|i (editor|-interface)
        proc|i (proc|-interface)
        log|i (log|-interface)
        editor|s-op (sub editor|p (-topic-extension-op editor|i) (chan 10))
        editor|s-cmd (sub editor|p (-topic-editor-cmd editor|i) (chan 10))
        unsubscribe #(do (unsub editor|p (-topic-editor-cmd editor|i) editor|s-op)
                         (unsub editor|p (-topic-editor-cmd editor|i) editor|s-cmd)
                         (close! editor|s-op)
                         (close! editor|s-cmd))]
    (>! proc| (-started proc|i))
    (go (loop [state {:tabs {:current nil}}]
          (if-let [[v port] (alts! [proc| editor|s-op editor|s-cmd])]
            (condp = port
              proc| (condp = (:op v)
                      (-op-stop proc|i) (let [{:keys [out|]} v]
                                          (unsubscribe)
                                          (>! out| (-stopped proc|i))
                                          (put! log| (-info log|i nil "proc-ops exiting" nil))))
              editor|s-op (let [op (:op v)]
                            (condp = op
                              (-op-activate editor|i) (do (>! editor| (-register-commands editor|i (default-commands))))
                              (-op-deactivate editor|i) (do (>! editor| (-show-info-msg editor|i "deactiavting"))))
                            (recur state))
              editor|s-cmd (let [cmd (:cmd/id v)]
                             (condp = cmd
                               "mult.activate" (do (>! editor| (-show-info-msg editor|i "mult.activate")))
                               "mult.ping" (do
                                             (>! editor| (-show-info-msg editor|i "mult.ping via channels"))
                                             (<! (timeout 3000))
                                             (>! editor| (-show-info-msg editor|i "mult.ping via channels later")))
                               "mult.counter"  (do (>! editor| (-show-info-msg editor|i "mult.counter"))))
                             (recur state))))))))