(ns cljctools.mult.protocols)

(defprotocol Release
  (release* [_]))


(defprotocol CljctoolsMult
  #_IDeref)

(defprotocol LogicalRepl
  (on-activate* [_ ns-symbol])
  (eval* [_ opts])
  #_Release
  #_IDeref)

(defprotocol Edit
  #_Release
  #_IDeref)

(defprotocol Active?
  (active?* [_]))

(defprotocol Visible?
  (visible?* [_]))

(defprotocol Send
  (send* [_ msg]))

(defprotocol Open
  (open* [_]))

(defprotocol Close
  (close* [_]))

(defprotocol Editor
  (show-notification* [_ text])
  (active-text-editor* [_])
  (create-tab* [_ opts])
  (read-mult-edn* [_])
  #_Release
  #_IDeref)

(defprotocol TextEditor
  (text* [_] [_ range])
  (filepath* [_])
  (selection* [_])
  (replace* [_ text]))

(defprotocol Tab
  #_Open
  #_Close
  #_Send
  #_Active?
  #_Visible?
  #_Release
  #_IDeref)

(defprotocol NreplConnection
  (eval* [_ opts])
  (clone* [_ opts])
  (connect* [_] [_ opts])
  (disconnect* [_])
  #_Release
  #_IDeref)