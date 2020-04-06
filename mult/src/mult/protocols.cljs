(ns mult.protocols)

(defprotocol Proc
  (-start [_])
  (-start [_ out|])
  (-stop [_])
  (-stop [_ out|])
  (-running? [_]))

(defprotocol Proc|
  (-op-start [_])
  (-op-stop [_])
  (-start [_ out|])
  (-stop [_ out|]))

(defprotocol Procs
  (-start [_ proc-id])
  (-stop [_ proc-id])
  (-restart [_ proc-id])
  (-up [_ ctx])
  (-down [_])
  (-downup [_ ctx])
  (-up? [_]))

(defprotocol Procs|
  (-op-start [_])
  (-op-stop [_])
  (-op-restart [_])
  (-op-started [_])
  (-op-stopped [_])
  (-op-error [_])
  (-op-up [_])
  (-op-down [_])
  (-op-downup [_])
  (-start [_ proc-id out|])
  (-stop [_ proc-id out|])
  (-started [_])
  (-stopped [_])
  (-error [_])
  (-restart [_ proc-id out|])
  (-up [_ ctx out|])
  (-down [_ out|])
  (-downup [_ ctx out|]))

(defprotocol Log|
  (-op-step [_])
  (-op-info [_])
  (-op-warning [_])
  (-op-error [_])
  (-step [_ id step-key comment data])
  (-info [_ id comment data])
  (-warning [_ id comment data])
  (-error [_ id comment data])
  (-explain [_ id result comment data]))

(defprotocol System|
  (-proc-started [_ v])
  (-proc-stopped [_ v]))

(defprotocol Ops|
  (-op-tab-add [_] )
  (-op-tab-on-dispose [_])
  (-activate [_ v])
  (-deactivate [_ v])
  (-tab-add [_ v])
  (-tab-send [_ v]))

(defprotocol Tab
  (-dispose [_]))

(defprotocol Connection
  (-connect [_])
  (-disconnect [_]))