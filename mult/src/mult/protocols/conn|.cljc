(ns mult.protocols.conn|)

(defprotocol Conn|
  (-op-eval [_])

  (-eval [_ code]))




