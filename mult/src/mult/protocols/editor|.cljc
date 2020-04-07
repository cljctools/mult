(ns mult.protocols.editor|)

(defprotocol Editor|
  (-op-show-info-msg [_])
  (-op-register-commands [_])
  (-op-create-repl-tab [_])

  (-show-info-msg [_ msg])
  (-register-commands [_ commands])
  (-create-repl-tab [_ tab-id]))

