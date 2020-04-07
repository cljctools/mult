(ns mult.protocols.channels)

(defprotocol Op
  (-op [_ v] "Returns the operation name (a keyword) of the value"))

(defprotocol Log|
  (-op-log [_])
  (-log [_  comment] [_ comment data]  [_ id comment data]))

(defprotocol Cmd|
  (-op-cmd [_])

  (-cmd [_ id args]))