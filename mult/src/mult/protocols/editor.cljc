(ns mult.protocols.editor)

(defprotocol Editor
  (-selection [_])
  (-register-commands [_ commands])
  (-create-tab [_ tabid])
  (-read-workspace-file [_ filepath])
  (-show-info-msg [_ msg])
  (-release! [_]))