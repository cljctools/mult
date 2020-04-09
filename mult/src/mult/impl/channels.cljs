(ns mult.impl.channels
  (:require
   [clojure.core.async :as a :refer [chan go go-loop <! >!  take! put! offer! poll! alt! alts! close!
                                     pub sub unsub mult tap untap mix admix unmix
                                     timeout to-chan  sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [goog.string :refer [format]]
   [clojure.string :as string]
   [cljs.reader :refer [read-string]]
   [mult.protocols.val :as p.val]))

(def ^:const TOPIC :topic)
(def ^:const OP :op)

(defn main|i
  []
  (let []
    (reify
      p.val/Op
      (-op [_ v] (get v OP))
      p.val/Init
      (-op-init [_] :main/init)
      (-init [_] {OP (p.val/-op-init _)})
      p.val/Activate
      (-op-activate [_] :main/activate)
      (-activate [_ editor-context] {OP (p.val/-op-activate _) :editor-context editor-context})
      p.val/Deactivate
      (-op-deactivate [_] :main/deactivate)
      (-deactivate [_] {OP (p.val/-op-deactivate _)})
      p.val/Start
      (-op-start [_] :main/start-proc)
      (-start [_ proc-fn] {OP (p.val/-op-start _) :proc-fn proc-fn})
      p.val/Stop
      (-op-stop [_] :main/stop-proc)
      (-stop [_ proc-id] {OP (p.val/-op-stop _) :proc-id proc-id})
      p.val/Restart
      (-op-restart [_] :main/restart-proc)
      (-restart [_ proc-id] {OP (p.val/-op-restart _) :proc-id proc-id})
      p.val/Started
      (-op-started [_] :main/proc-started)
      (-started [_ proc-id proc|] {OP (p.val/-op-started _) :proc-id proc-id :proc| proc|})
      p.val/Stopped
      (-op-stopped [_] :main/proc-stopped)
      (-stopped [_ proc-id] {OP (p.val/-op-stopped _) :proc-id proc-id}))))


(defn log|i
  []
  (let []
    (reify
      p.val/Op
      (-op [_ v] (get v OP))
      p.val/Log
      (-op-log [_] :log)
      (-log [_ comment] {OP (p.val/-op-log _) :comment comment})
      (-log [_ comment data] {OP (p.val/-op-log _) :comment comment :data data})
      (-log [_ id  comment data] {OP (p.val/-op-log _) :id id :comment comment :data data}))))

(defn ops|i
  []
  (let []
    (reify
      p.val/Op
      (-op [_ v] (get v OP))
      p.val/Activate
      (-op-activate [_] :ops/activate)
      (-activate [_] {OP (p.val/-op-activate _)})
      p.val/Deactivate
      (-op-deactivate [_] :ops/deactivate)
      (-deactivate [_] {OP (p.val/-op-deactivate _)})
      p.val/Ops
      (-op-tab-created [_] :ops/tab-created)
      (-op-tab-disposed [_] :ops/tab-disposed)
      (-op-read-conf-result [_] :ops/read-file-result)
      (-tab-created [_ tab] {OP (p.val/-op-tab-created _) :tab tab})
      (-tab-disposed [_ id] {OP (p.val/-op-tab-disposed _) :tab/id id})
      (-read-conf-result [_ conf args] {OP (p.val/-op-read-conf-result _) :conf conf :args args}))))

(defn cmd|i
  []
  (let []
    (reify
      p.val/Op
      (-op [_ v] (get v OP))
      p.val/Cmd
      (-op-cmd [_] :cmd/cmd)
      (-cmd [_ id args] {OP (p.val/-op-cmd _) :cmd/id id}))))

(defn editor|i
  []
  (let []
    (reify
      p.val/Op
      (-op [_ v] (get v OP))
      p.val/Editor
      (-op-show-info-msg [_] :editor/show-info-msg)
      (-op-register-commands [_] :editor/register-commands)
      (-op-create-tab [_] :editor/create-tab)
      (-op-read-conf [_] :editor/read-file)

      (-show-info-msg [_ msg] {OP (p.val/-op-show-info-msg _) :msg msg})
      (-register-commands [_ commands] {OP (p.val/-op-register-commands _) :commands commands})
      (-create-tab [_ tab-id] {OP (p.val/-op-create-tab _) :tab/id tab-id})
      (-read-conf [_  filepath out|] {OP (p.val/-op-read-conf _) :filepath filepath :out| out|}))))

(defn tab|i
  []
  (let []
    (reify
      p.val/Op
      (-op [_ v] (get v OP))
      p.val/Clear
      (-op-clear [_] :tab/clear)
      (-clear [_] {OP (p.val/-op-clear _)})
      p.val/Append
      (-op-append [_] :tab/append)
      (-append [_ data] {OP (p.val/-op-append _) :data data})
      p.val/Conf
      (-op-conf [_] :tab/conf)
      (-conf [_ conf] {OP (p.val/-op-conf _) :conf conf}))))
