(ns mult.protocols.tab|)

(defprotocol Tab|
  (-op-clear [_])
  (-op-append [_])

  (-clear [_])
  (-append [_ data]))

 


