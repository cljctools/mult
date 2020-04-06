(ns mult.protocols.editor)

(defprotocol Editor|
  (-topic-editor-op [_])
  (-topic-editor-cmd [_])
  (-topic-extension-op [_])
  (-topic-tab [_])

  (-op-activate [_])
  (-op-deactivate [_])
  (-op-show-info-msg [_])
  (-op-register-commands [_])
  (-op-open-repl-tab [_])
  (-op-cmd [_])
  (-op-tab-clear [_])
  (-op-tab-append [_])
  (-op-tab-disposed [_])

  (-activate [_ ctx])
  (-deactivate [_])
  (-show-info-msg [_ msg])
  (-register-commands [_ commands])
  (-open-repl-tab [_ tab-id])
  (-cmd [_ id args])
  (-tab-clear [_ id])
  (-tab-append [_])
  (-tab-disposed [_ id])
  (-tab-on-message [_ id msg]))

(defprotocol Paredit
  (-abc [_]))