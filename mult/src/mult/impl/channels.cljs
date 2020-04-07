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
   [mult.protocols.main| :as p.main|]
   [mult.protocols.tab| :as p.tab|]
   [mult.protocols.conn| :as p.conn|]))

(def ^:const TOPIC :topic)
(def ^:const OP :op)

(defn main|i
  []
  (let []
    (reify
      p.channels/Op
      (-op [_ v] (get v OP))
      p.main|/Main|
      (-op-init [_] :main/init)
      (-op-activate [_] :main/activate)
      (-op-deactivate [_] :main/deactivate)
      (-op-start-proc [_] :main/start-proc)
      (-op-stop-proc [_] :main/stop-proc)
      (-op-restart-proc [_] :main/restart-proc)
      (-op-proc-started [_] :main/proc-started)
      (-op-proc-stopped [_] :main/proc-stopped)

      (-init [_] {OP (p.main|/-op-init _)})
      (-activate [_ editor-context] {OP (p.main|/-op-activate _) :editor-context editor-context})
      (-deactivate [_] {OP (p.main|/-op-deactivate _)})
      (-start-proc [_ proc-fn] {OP (p.main|/-op-start-proc _) :proc-fn proc-fn})
      (-stop-proc [_ proc-id] {OP (p.main|/-op-stop-proc _) :proc-id proc-id})
      (-restart-proc [_ proc-id] {OP (p.main|/-op-restart-proc _) :proc-id proc-id})
      (-proc-started [_ proc-id proc|] {OP (p.main|/-op-proc-started _) :proc-id proc-id :proc| proc|})
      (-proc-stopped [_ proc-id] {OP (p.main|/-op-proc-stopped _) :proc-id proc-id}))))

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
      (-op-tab-created [_] :ops/tab-created)
      (-op-tab-disposed [_] :ops/tab-disposed)
      (-op-eval-result [_] :ops/eval-result)
      (-op-read-conf-result [_] :ops/read-file-result)

      (-activate [_] {OP (p.ops|/-op-activate _)})
      (-deactivate [_] {OP (p.ops|/-op-deactivate _)})
      (-tab-created [_ tab] {OP (p.ops|/-op-tab-created _) :tab tab})
      (-tab-disposed [_ id] {OP (p.ops|/-op-tab-disposed _) :tab/id id})
      (-eval-result [_ result] {OP (p.ops|/-op-eval-result _) :result result})
      (-read-conf-result [_ conf args] {OP (p.ops|/-op-read-conf-result _) :conf conf :args args}))))

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
      (-op-create-tab [_] :editor/create-tab)
      (-op-read-conf [_] :editor/read-file)

      (-show-info-msg [_ msg] {OP (p.editor|/-op-show-info-msg _) :msg msg})
      (-register-commands [_ commands] {OP (p.editor|/-op-register-commands _) :commands commands})
      (-create-tab [_ tab-id] {OP (p.editor|/-op-create-tab _) :tab/id tab-id})
      (-read-conf [_  filepath out|] {OP (p.editor|/-op-read-conf _) :filepath filepath :out| out|} ))))

(defn tab|i
  []
  (let []
    (reify
      p.channels/Op
      (-op [_ v] (get v OP))
      p.tab|/Tab|
      (-op-clear [_] :tab/clear)
      (-op-append [_] :tab/append)
      (-op-conf [_] :tab/conf)

      (-clear [_] {OP (p.tab|/-op-clear _)})
      (-append [_ data] {OP (p.tab|/-op-append _) :data data})
      (-conf [_ conf] {OP (p.tab|/-op-conf _) :conf conf}))))

(defn conn|i
  []
  (let []
    (reify
      p.channels/Op
      (-op [_ v] (get v OP))
      p.conn|/Conn|
      (-op-eval [_] :conn/eval)
      (-eval [_ code] {OP (p.conn|/-op-eval _) :code code}))))
