(ns mult.protocols.channels)

(defprotocol Op
  (-op [_ v] "Returns the operation name (a keyword) of the value"))

(defprotocol Log|
  (-op-log [_])
  (-log [_  comment] [_ comment data]  [_ id comment data]))

(defprotocol Main|
  (-op-init [_])
  (-op-activate [_])
  (-op-deactivate [_])
  (-op-start-proc [_])
  (-op-stop-proc [_])
  (-op-restart-proc [_])
  (-op-proc-started [_])
  (-op-proc-stopped [_])

  (-init [_])
  (-activate [_ editor-context])
  (-deactivate [_])
  (-start-proc [_ proc-fn])
  (-stop-proc [_ proc-id])
  (-restart-proc [_ proc-id])
  (-proc-started [_ proc-id proc|])
  (-proc-stopped [_ proc-id]))

(defprotocol Cmd|
  (-op-cmd [_])

  (-cmd [_ id args]))