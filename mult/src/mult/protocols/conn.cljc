(ns mult.protocols.conn)

(defprotocol Conn
  (-connect [_])
  (-disconnect [_])
  (-eval [_ code]))

