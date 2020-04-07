(ns mult.protocols.core)


(defprotocol Tab
  (-dispose [_]))

(defprotocol Connection
  (-connect [_])
  (-disconnect [_]))