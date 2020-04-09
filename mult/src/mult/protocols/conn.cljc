(ns mult.protocols.conn)

(defprotocol Conn
  (-connect [_ ])
  (-disconnect [_])
  (-send [_ v]))

(defprotocol NRepl
    (-clone-session [_ opts])
    (-close-session [_ session-id opts])
    (-describe [_  opts])
    (-eval [_ code session-id opts])
    (-interrupt [_ session-id opts])
    (-load-file [_ file opts])
    (-ls-sessions [_])
    (-sideloader-provide [_ content name session-id type opts])
    (-sideloader-start [_ session-id opts])
    (-stdin [_ stdin-content opts]))