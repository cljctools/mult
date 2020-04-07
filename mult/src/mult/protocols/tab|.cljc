(ns mult.protocols.tab|)

(defprotocol Tab|
  (-op-clear [_])
  (-op-append [_])
  (-op-conf [_])

  (-clear [_])
  (-append [_ data])
  (-conf [_ conf]))

 


