(ns mult.protocols.ops|)

(defprotocol Ops|
  (-op-activate [_])
  (-op-deactivate [_])
  (-op-repl-tab-created [_])
  (-op-tab-disposed [_])

  (-activate [_])
  (-deactivate [_])
  (-repl-tab-created [_ tab])
  (-tab-disposed [_ id]))