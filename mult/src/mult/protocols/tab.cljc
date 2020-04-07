(ns mult.protocols.tab)

(defprotocol Tab
  :extend-via-metadata true
  (-put! [_ v])
  (-dispose [_]))

