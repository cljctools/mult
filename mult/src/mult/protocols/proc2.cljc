(ns mult.protocols.proc2)



(defprotocol Start
  (-start [_] [_ out|]))
(defprotocol Stop
  (-stop [_] [_ out|]))
(defprotocol Running?
  (-running? [_]))

(defprotocol OpStartKey
  (-op-start-key [_]))
(defprotocol OpStopKey
  (-op-stop-key [_]))
(defprotocol OpStartedKey
  (-op-started-key [_]))
(defprotocol OpStartedKey
  (-op-stopped-key [_]))

(defprotocol OpStartVal
  (-op-start-val [_]))
(defprotocol OpStopVal
  (-op-stop-val [_]))
(defprotocol OpStartedVal
  (-op-started-val [_]))
(defprotocol OpStoppedVal
  (-op-stopped-val [_]))
(defprotocol OpStoppedVal
  (-op-stopped-val [_]))


(defprotocol Procs
  (-start-proc [_ proc-id])
  (-stop-proc [_ proc-id])
  (-restart-proc [_ proc-id])
  (-up [_ ctx])
  (-down [_])
  (-downup [_ ctx])
  (-up? [_]))

(defprotocol Procs|
  (-op-start-proc-key [_])
  (-op-stop-proc-key [_])
  (-op-restart-proc-key [_])
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