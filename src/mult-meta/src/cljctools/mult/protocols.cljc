(ns cljctools.mult.protocols)

(defprotocol Release
  (release* [_]))


(defprotocol CljctoolsMult
  #_IDeref)

(defprotocol LogicalRepl
  (on-activate* [_ ns-symbol])
  (eval* [_ opts])
  #_Release
  #_IDeref)


