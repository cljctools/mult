(ns cljctools.mult.nrepl.protocols)

(defprotocol Release
  (release* [_]))

(defprotocol NreplConnection
  (eval* [_ opts])
  (connect* [_] [_ opts])
  (disconnect* [_])
  #_Release
  #_IDeref)