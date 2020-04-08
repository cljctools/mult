(ns mult.protocols.conn)

(defprotocol Conn
  (-connect [_])
  (-disconnect [_]))

