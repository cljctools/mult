(ns mult.impl.channels
  (:require
   [clojure.core.async :as a :refer [chan go go-loop <! >!  take! put! offer! poll! alt! alts! close!
                                     pub sub unsub mult tap untap mix admix unmix
                                     timeout to-chan  sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [goog.string :refer [format]]
   [clojure.string :as string]
   [cljs.reader :refer [read-string]]
   [mult.protocols.editor| :as p.editor|]
   [mult.protocols.channels :as p.channels]
   [mult.protocols.ops| :as p.ops|]
   [mult.protocols.tab| :as p.tab|]))

(def ^:const TOPIC :topic)
(def ^:const OP :op)

(defn main|i
  []
  (let []
    (reify
      p.channels/Op
      (-op [_ v] (get v OP))
      p.channels/Main|
      (-op-init [_] :main/init)
      (-op-activate [_] :main/activate)
      (-op-deactivate [_] :main/deactivate)
      (-op-start-proc [_] :main/start-proc)
      (-op-stop-proc [_] :main/stop-proc)
      (-op-restart-proc [_] :main/restart-proc)
      (-op-proc-started [_] :main/proc-started)
      (-op-proc-stopped [_] :main/proc-stopped)

      (-init [_] {OP (p.channels/-op-init _)})
      (-activate [_ editor-context] {OP (p.channels/-op-activate _) :editor-context editor-context})
      (-deactivate [_] {OP (p.channels/-op-deactivate _)})
      (-start-proc [_ proc-fn] {OP (p.channels/-op-start-proc _) :proc-fn proc-fn})
      (-stop-proc [_ proc-id] {OP (p.channels/-op-stop-proc _) :proc-id proc-id})
      (-restart-proc [_ proc-id] {OP (p.channels/-op-restart-proc _) :proc-id proc-id})
      (-proc-started [_ proc-id proc|] {OP (p.channels/-op-proc-started _) :proc-id proc-id :proc| proc|})
      (-proc-stopped [_ proc-id] {OP (p.channels/-op-proc-stopped _) :proc-id proc-id}))))

(defn log|i
  []
  (let []
    (reify
      p.channels/Op
      (-op [_ v] (get v OP))
      p.channels/Log|
      (-op-log [_] :log/log)
      (-log [_ comment] {OP (p.channels/-op-log _) :comment comment})
      (-log [_ comment data] {OP (p.channels/-op-log _) :comment comment :data data})
      (-log [_ id  comment data] {OP (p.channels/-op-log _) :id id :comment comment :data data}))))

(defn ops|i
  []
  (let []
    (reify
      p.channels/Op
      (-op [_ v] (get v OP))
      p.ops|/Ops|
      (-op-activate [_] :ops/activate)
      (-op-deactivate [_] :ops/deactivate)
      (-op-repl-tab-created [_] :ops/repl-tab-created)
      (-op-tab-disposed [_] :ops/tab-disposed)

      (-activate [_] {OP (p.ops|/-op-activate _)})
      (-deactivate [_] {OP (p.ops|/-op-deactivate _)})
      (-repl-tab-created [_ tab] {OP (p.ops|/-op-repl-tab-created _) :tab tab})
      (-tab-disposed [_ id] {OP (p.ops|/-op-tab-disposed _) :tab/id id}))))

(defn cmd|i
  []
  (let []
    (reify
      p.channels/Op
      (-op [_ v] (get v OP))
      p.channels/Cmd|
      (-op-cmd [_] :cmd/cmd)
      (-cmd [_ id args] {OP (p.channels/-op-cmd _) :cmd/id id}))))

(defn editor|i
  []
  (let []
    (reify
      p.channels/Op
      (-op [_ v] (get v OP))
      p.editor|/Editor|
      (-op-show-info-msg [_] :editor/show-info-msg)
      (-op-register-commands [_] :editor/register-commands)
      (-op-create-repl-tab [_] :editor/create-repl-tab)

      (-show-info-msg [_ msg] {OP (p.editor|/-op-show-info-msg _) :msg msg})
      (-register-commands [_ commands] {OP (p.editor|/-op-register-commands _) :commands commands})
      (-create-repl-tab [_ tab-id] {OP (p.editor|/-op-create-repl-tab _) :tab/id tab-id}))))

(defn tab|i
  []
  (let []
    (reify
      p.channels/Op
      (-op [_ v] (get v OP))
      p.tab|/Tab|
      (-op-clear [_] :tab/clear)
      (-op-append [_] :tab/append)

      (-clear [_] {OP (p.tab|/-op-clear _)})
      (-append [_ data] {OP (p.tab|/-op-append _) :data data}))))

