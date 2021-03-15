(ns cljctools.mult.protocols)

(defprotocol Release
  (release* [_]))


(defprotocol CljctoolsMult
  #_IDeref)

(defprotocol LogicalRepl
  (eval* [_ opts])
  (on-activate* [_ ns-symbol])
  #_Release
  #_IDeref)

