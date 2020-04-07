(ns mult.protocols.tab)

(defprotocol Tab
  (-put! [_ v])
  (-dispose [_]))

