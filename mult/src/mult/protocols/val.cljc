(ns mult.protocols.val)

; rationale 
; multiple channels may need to implement e.g. status values 
; (connected disconnected close open error ...)
; or start stop 
; or started stopped 
; and eval results 
; etc.
; to know what values are valid for a channel see impl.channels


(defprotocol Op
  (-op [_ v] "Returns the operation name (a keyword) of the value"))

(defprotocol Init
  (-op-init [_])
  (-init [_]))

(defprotocol Activate
  (-op-activate [_])
  (-activate [_] [_ a]))

(defprotocol Deactivate
  (-op-deactivate [_])
  (-deactivate [_] [_ a]))

(defprotocol Start
  (-op-start [_])
  (-start [_] [_ a]))

(defprotocol Stop
  (-op-stop [_])
  (-stop [_] [_ a]))

(defprotocol Restart
  (-op-restart [_])
  (-restart [_] [_ a]))

(defprotocol Started
  (-op-started [_])
  (-started [_] [_ a] [_ a b]))

(defprotocol Stopped
  (-op-stopped [_])
  (-stopped [_] [_ a] [_ a b]))

(defprotocol Restarted
  (-op-restarted [_])
  (-restarted [_] [_ a]))

(defprotocol Create
  (-op-create [_] )
  (-create [_] [_ a]))

(defprotocol Dispose
  (-op-dispose [_])
  (-dispose [_] [_ a]))

(defprotocol Created
  (-op-created [_])
  (-created [_] [_ a]))

(defprotocol Disposed
  (-op-disposed [_])
  (-disposed [_] [_ a]))

(defprotocol Connect
  (-op-connect [_])
  (-connect [_] [_ a]))

(defprotocol Disconnect
  (-op-disconnect [_])
  (-disconnect [_] [_ a]))

(defprotocol Connected
  (-op-connected [_])
  (-connected [_] [_ a]))

(defprotocol Disconnected
  (-op-disconnected [_])
  (-disconnected [_] [_ a]))

(defprotocol Ready
  (-op-ready [_])
  (-ready [_] [_ a]))

(defprotocol Timeout
  (-op-timeout [_])
  (-timeout [_] [_ a]))

(defprotocol Closed
  (-op-closed [_])
  (-closed [_] [_ a]))

(defprotocol Data
  (-op-data [_])
  (-data [_] [_ a]))

(defprotocol Error
  (-op-error[_])
  (-error [_] [_ a]))

(defprotocol Log
  (-op-log [_])
  (-log [_  comment] [_ comment data]  [_ id comment data]))

(defprotocol Cmd
  (-op-cmd [_])
  (-cmd [_ id args]))

(defprotocol Ops
  (-op-read-conf-result [_])
  (-read-conf-result [_ result args])
  (-op-tab-created [_])
  (-op-tab-disposed [_])
  (-tab-created [_ tab])
  (-tab-disposed [_ id]))

(defprotocol Editor
  (-op-show-info-msg [_])
  (-op-register-commands [_])
  (-op-create-tab [_])
  (-op-read-conf [_])
  (-show-info-msg [_ msg])
  (-register-commands [_ commands])
  (-create-tab [_ tab-id])
  (-read-conf [_ filepath out|]))


(defprotocol Clear
  (-op-clear [_])
  (-clear [_]))

(defprotocol Append
  (-op-append [_])
  (-append [_ data] ))

(defprotocol Conf
  (-op-conf [_])
  (-conf [_ conf]))
