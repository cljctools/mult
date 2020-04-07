(ns mult.protocols.ops|)

(defprotocol Ops|
  (-op-activate [_])
  (-op-deactivate [_])
  (-op-tab-created [_])
  (-op-tab-disposed [_])
  (-op-eval-result [_])
  (-op-read-conf-result [_])

  (-activate [_])
  (-deactivate [_])
  (-tab-created [_ tab])
  (-tab-disposed [_ id])
  (-eval-result [_ result])
  (-read-conf-result [_ result args]))