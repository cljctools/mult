(ns mult.extension
  (:require
   [clojure.core.async :as a :refer [<! >!  chan go alt! take! put! offer! poll! alts! pub sub
                                     timeout close! to-chan go-loop sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [goog.string :refer [format]]
   [clojure.string :as string]
   ["fs" :as fs]
   ["path" :as path]
   ["net" :as net]
   ["bencode" :as bencode]
   [cljs.reader :refer [read-string]]
   [bencode-cljc.core :refer [serialize deserialize]]
   [mult.protocols :refer [Proc]]
   [mult.proc.impl :refer [procs-impl proc-log]]
   [mult.impl :refer [show-information-message register-commands]]))

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
            {:procs {:proc-ops {:proc-fn #'proc-ops
                                :ctx-fn #(-> % (select-keys [:channels  :vscode :vscode-context])
                                             (update :channels #(select-keys % [:cmd| :ops|])))}
                     :proc-log {:proc-fn #'proc-log
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
    #_(-up procs {:channels channels
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

(defn default-commands
  []
  ["mult.activate"
   "mult.ping"
   "mult.deactivate"
   "mult.counter"])

(defn proc-ops
  [{:keys [cmd| ops| system|pub]} vscode context]
  (let [sys| (chan 1)]
    (sub system|pub :system sys|)
    (go (loop []
          (if-let [[v port] (alts! [cmd| ops| sys|])]
            (condp = port
              sys| (condp = (:proc/op v)
                     :exit (do nil))
              cmd| (let [{:keys [cmd/id cmd/args]} v]
                     (println (format "; cmd/id %s" id))
                     (condp = id
                       "mult.activate" (do
                                         (show-information-message vscode "mult.activate")
                                         (>! ops| {:op :tab/add}))
                       "mult.ping" (do
                                     (show-information-message vscode "mult.ping via channels")
                                     (println "in 3sec will show another msg")
                                     (<! (timeout 3000))
                                     (show-information-message vscode "mult.ping via channels later"))
                       "mult.counter" (do
                                        (put! ops| {:op :tab/send
                                                    :tab/id :current
                                                    :tab/msg {:op :tabapp/inc}})))
                     (recur))
              ops| (let [{:keys [op]} v]
                     (println (format "; proc-ops %s" op))
                     (condp = op
                       :activate (let []
                                   (register-commands (default-commands) vscode context cmd|))
                       :deactivate (let []
                                     (put! (channels :system|) {:ch/topic :system :proc/op :exit}))
                       :tab/add (let [id (random-uuid)
                                      tab (make-tab vscode context id ops|)]
                                  (swap! state update-in [:tabs] assoc id tab)
                                  (swap! state update-in [:tabs] assoc :current tab))
                       :tab/on-dispose (let [{:keys [tab/id]} v]
                                         (swap! state update-in [:tabs] dissoc id))
                       :tab/on-message (let [{:keys [tab/msg]} v]
                                         (println msg))
                       :tab/send (let [{:keys [tab/id tab/msg]} v
                                       tab (get-in @state [:tabs id])]
                                   (.postMessage (.-webview tab) (str msg))))
                     (recur)))))
        (println "proc-ops exiting"))))

