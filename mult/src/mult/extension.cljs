(ns mult.extension
  (:require
   [clojure.core.async :as a :refer [chan go go-loop <! >!  take! put! offer! poll! alt! alts! close!
                                     pub sub unsub mult tap untap mix admix unmix
                                     timeout to-chan  sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [goog.string :refer [format]]
   [clojure.string :as string]
   [cljs.reader :refer [read-string]]
   
   [mult.protocols.proc| :as p.proc|]
   [mult.protocols.channels :as p.channels]
   [mult.protocols.editor| :as p.editor|]
   [mult.protocols.procs :as p.procs]

   [mult.impl.proc :as proc]
   [mult.impl.editor :as editor]
   [mult.impl.channels :as channels]))

(def channels (channels/make-channels))

(declare proc-ops)

(def procs (proc/procs-impl
            {:ctx {:channels channels}
             :procs {:proc-ops {:proc-fn #'proc-ops
                                :ctx-fn identity
                                #_(fn [ctx]
                                    (-> % (select-keys [:channels  :editor-ctx])
                                        (update :channels #(select-keys % [:cmd| :ops|]))))}
                     :proc-log {:proc-fn #'proc/proc-log
                                :ctx-fn identity}
                     :proc-editor {:proc-fn #'editor/proc-editor
                                   :ctx-fn identity}}
             :up (fn [procs ctx ]
                   (go
                     (->> [(p.procs/-start procs  :proc-ops)]
                          (a/map vector)
                          (<!))
                     (<! (p.procs/-start procs  :proc-log))))
             :down (fn [procs ctx]
                     (go
                       (<! (p.procs/-stop procs  :proc-log))))}))

(defn activate
  [context]
  (when-not (not (p.procs/-up? procs))
    #_(-up procs {:channels channels
                  :editor-ctx context}))
  (put! (channels :ops|) {:op :activate}))

(defn deactivate []
  (go
    (let [c| (chan 1)]
      (>! (channels :ops|) {:op :deactivate :c| c|})
      (<! c|)
      (when (p.procs/-up? procs)
        (p.procs/-down procs)))))

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
        editor|i (channels/editor|i)
        proc|i (channels/proc|i)
        log|i (channels/log|i)
        editor|s-op (sub editor|p (p.editor|/-topic-extension-op editor|i) (chan 10))
        editor|s-cmd (sub editor|p (p.editor|/-topic-editor-cmd editor|i) (chan 10))
        unsubscribe #(do (unsub editor|p (p.editor|/-topic-editor-cmd editor|i) editor|s-op)
                         (unsub editor|p (p.editor|/-topic-editor-cmd editor|i) editor|s-cmd)
                         (close! editor|s-op)
                         (close! editor|s-cmd))]
    (>! proc| (p.proc|/-started proc|i))
    (go (loop [state {:tabs {:current nil}}]
          (if-let [[v port] (alts! [proc| editor|s-op editor|s-cmd])]
            (condp = port
              proc| (condp = (:op v)
                      (p.proc|/-op-stop proc|i) (let [{:keys [out|]} v]
                                                  (unsubscribe)
                                                  (>! out| (p.proc|/-stopped proc|i))
                                                  (put! log| (p.channels/-info log|i nil "proc-ops exiting" nil))))
              editor|s-op (let [op (:op v)]
                            (condp = op
                              (p.editor|/-op-activate editor|i) (do (>! editor| (p.editor|/-register-commands editor|i (default-commands))))
                              (p.editor|/-op-deactivate editor|i) (do (>! editor| (p.editor|/-show-info-msg editor|i "deactiavting"))))
                            (recur state))
              editor|s-cmd (let [cmd (:cmd/id v)]
                             (condp = cmd
                               "mult.activate" (do (>! editor| (p.editor|/-show-info-msg editor|i "mult.activate")))
                               "mult.ping" (do
                                             (>! editor| (p.editor|/-show-info-msg editor|i "mult.ping via channels"))
                                             (<! (timeout 3000))
                                             (>! editor| (p.editor|/-show-info-msg editor|i "mult.ping via channels later")))
                               "mult.counter"  (do (>! editor| (p.editor|/-show-info-msg editor|i "mult.counter"))))
                             (recur state))))))))