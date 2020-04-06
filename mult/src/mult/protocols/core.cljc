(ns mult.protocols.core)

(defprotocol Ops|
  (-op-tab-add [_])
  (-op-tab-on-dispose [_])
  (-activate [_ v])
  (-deactivate [_ v])
  (-tab-add [_ v])
  (-tab-send [_ v]))

(defprotocol Tab
  (-dispose [_]))

(defprotocol Connection
  (-connect [_])
  (-disconnect [_]))