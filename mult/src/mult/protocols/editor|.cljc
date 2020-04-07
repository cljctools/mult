(ns mult.protocols.editor|)

(defprotocol Editor|
  (-op-show-info-msg [_])
  (-op-register-commands [_])
  (-op-create-tab [_])
  (-op-read-conf [_])

  (-show-info-msg [_ msg])
  (-register-commands [_ commands])
  (-create-tab [_ tab-id])
  (-read-conf [_ filepath out|]))