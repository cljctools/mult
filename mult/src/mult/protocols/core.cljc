(ns mult.protocols.conn)




(defprotocol Conn
  :extend-via-metadata true
  (-connect [_])
  (-disconnect [_])
  (-send [_ v])
  (-connected? [_]))

(defprotocol NRepl
  #_(-clone-session [_ opts])
  (-close-session [_ session-id opts])
  (-describe [_  opts])
  (-eval [_ code session-id opts])
  (-interrupt [_ session-id opts])
  #_(-load-file [_ file opts])
  (-ls-sessions [_])
  #_(-sideloader-provide [_ content name session-id type opts])
  #_(-sideloader-start [_ session-id opts])
  #_(-stdin [_ stdin-content opts]))

(defprotocol Tab
  :extend-via-metadata true
  (-put! [_ v])
  (-dispose [_]))

(defprotocol LRepl
  (-eval [_ opts code]))

(defprotocol Editor
  (-selection [_])
  (-register-commands [_ commands])
  (-create-tab [_ tabid])
  (-read-workspace-file [_ filepath])
  (-show-info-msg [_ msg])
  (-release [_]))
