(ns mult.impl.channels
  (:require
   [clojure.core.async :as a :refer [chan go go-loop <! >!  take! put! offer! poll! alt! alts! close!
                                     pub sub unsub mult tap untap mix admix unmix
                                     timeout to-chan  sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [goog.string :refer [format]]
   [clojure.string :as string]
   [cljs.reader :refer [read-string]]
   [mult.protocols.proc| :as p.proc|]
   [mult.protocols.procs| :as p.procs|]
   [mult.protocols.editor| :as p.editor|]
   [mult.protocols.channels :as p.channels]
   ))

(defn make-channels []
  (let [system| (chan (sliding-buffer 10))
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


(def ^:const TOPIC :topic)
(def ^:const OP :op)

(defn proc|i
  []
  (reify
    p.proc|/Proc|
    (-op-start [_] :proc/start)
    (-op-stop [_] :proc/stop)
    (-op-started [_] :proc/started)
    (-op-stopped [_] :proc/stopped)
    (-start [_ out|] {OP (p.proc|/-op-start _) :out| out|})
    (-stop [_ out|] {OP (p.proc|/-op-stop _) :out| out|})
    (-started [_] {OP (p.proc|/-op-started _)})
    (-stopped [_] {OP (p.proc|/-op-stopped _)})))

(defn procs|i
  []
  (let []
    (reify
      p.procs|/Procs|
      (-op-start [_] :proc/start)
      (-op-stop [_] :proc/stop)
      (-op-started [_] :proc/started)
      (-op-stopped [_] :proc/stopped)
      (-op-error [_] :proc/error)
      (-op-restart [_] :proc/restart)
      (-op-up [_] :procs/up)
      (-op-down [_] :procs/down)
      (-op-downup [_] :procs/downup)
      (-start [_ proc-id out|] {OP (p.procs|/-op-start _) :proc/id proc-id :out| out|})
      (-stop [_ proc-id out|] {OP (p.procs|/-op-stop _) :proc/id proc-id :out| out|})
      (-started [_ proc-id] {OP (p.procs|/-op-started _) :proc/id proc-id})
      (-stopped [_ proc-id] {OP (p.procs|/-op-stopped _) :proc/id proc-id})
      (-error [_ proc-id err] {OP (p.procs|/-op-error _) :proc/id proc-id :error err})
      (-restart [_ proc-id out|] {OP (p.procs|/-op-restart _) :proc/id proc-id :out| out|})
      (-up [_ procs ctx out|] {OP (p.procs|/-op-up _) :ctx ctx :procs procs :out| out|})
      (-down [_  procs ctx out|] {OP (p.procs|/-op-down _) :ctx ctx :procs procs :out| out|}))))

(defn system|i
  []
  (let []
    (reify
      p.channels/System|
      (-proc-started [_ proc-id data]
        {:ch/topic [proc-id :started] :data data})
      (-proc-stopped [_ proc-id data]
        {:ch/topic [proc-id :stopped] :data data})
      (-procs-up [_]
        {:ch/topic :procs/up})
      (-procs-down [_]
        {:ch/topic :procs/down}))))

(defn log|i
  []
  (let []
    (reify
      p.channels/Log|
      (-op-step [_] :log/step)
      (-op-info [_] :log/info)
      (-op-warning [_] :log/warning)
      (-op-error [_] :log/error)
      (-step [_ id  comment data]
        {OP (p.channels/-op-step _) :log/comment comment :log/data data})
      (-info [_ id comment data]
        {OP (p.channels/-op-info _) :id id :log/comment comment :log/data data})
      (-warning [_ id comment data]
        {OP (p.channels/-op-warning _) :id id :log/comment comment :log/data data})
      (-error [_ id comment data]
        {OP (p.channels/-op-error _) :id id :log/comment comment :log/data data})
      (-explain [_ id result comment data]
        {:id id :result result  :comment comment :data data}))))

(defn editor|i
  []
  (let []
    (reify
      p.editor|/Editor|
      (-topic-editor-op [_] :editor/op)
      (-topic-extension-op [_] :extention/op)
      (-topic-editor-cmd [_] :editor/cmd)
      (-topic-tab [_] :tab/op)

      (-op-activate [_] :extention/activate)
      (-op-deactivate [_] :extention/deactivate)
      (-op-show-info-msg [_] :editor/show-info-msg)
      (-op-register-commands [_] :editor/register-commands)
      (-op-open-repl-tab [_] :editor/open-repl-tab)
      (-op-cmd [_] :editor/cmd)
      (-op-tab-clear [_] :tab/clear)
      (-op-tab-append [_] :tab/append)
      (-op-tab-disposed [_] :tab/disposed)

      (-activate [_ ctx] {OP (p.editor|/-op-activate _) :ctx ctx TOPIC (p.editor|/-topic-extension-op _)})
      (-deactivate [_] {OP (p.editor|/-op-deactivate _) TOPIC (p.editor|/-topic-extension-op _)})
      (-show-info-msg [_ msg] {OP (p.editor|/-op-show-info-msg _) :msg msg TOPIC (p.editor|/-topic-editor-op _)})
      (-register-commands [_ commands] {OP (p.editor|/-op-register-commands _) :commands commands TOPIC (p.editor|/-topic-editor-op _)})
      (-open-repl-tab [_ tab-id] {OP (p.editor|/-op-open-repl-tab _) :tab/id tab-id TOPIC (p.editor|/-topic-editor-op _)})
      (-cmd [_ id args] {OP (p.editor|/-op-cmd _) :cmd/id id :cmd/args args TOPIC (p.editor|/-topic-editor-op _)})
      (-tab-clear [_ id] {OP (p.editor|/-op-tab-clear _) :tab/id id  TOPIC (p.editor|/-topic-tab _)})
      (-tab-append [_ id data] {OP (p.editor|/-op-tab-append _) :tab/id id :data data TOPIC (p.editor|/-topic-tab _)})
      (-tab-disposed [_ id] {OP (p.editor|/-op-tab-disposed _) :tab/id id TOPIC (p.editor|/-topic-tab _)})
      (-tab-on-message [_ id msg] (merge msg {:tab/id id TOPIC (p.editor|/-topic-tab _)})))))