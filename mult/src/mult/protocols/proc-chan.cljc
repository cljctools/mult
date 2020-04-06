(ns mult.protocols.proc-chan)

(defprotocol ProcChan
  (-op-start-key [_])
  (-op-stop-key [_])
  (-op-started-key [_])
  (-op-stopped-key [_])

  (-op-start [_ out|])
  (-op-stop [_ out|])
  (-op-started [_])
  (-op-stopped [_]))