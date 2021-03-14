(ns cljctools.mult.protocols)

(defprotocol Release
  (release* [_]))

(defprotocol Active?
  (active?* [_]))

(defprotocol Send
  (send* [_ msg]))

(defprotocol Open
  (open* [_]))

(defprotocol Close
  (close* [_]))

(defprotocol CljctoolsMult
  #_IDeref)

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
  (selection* [_]))

(defprotocol Tab
  #_Open
  #_Close
  #_Send
  #_Active?
  #_Release
  #_IDeref)


(defprotocol LogicalRepl
  (eval* [_ opts])
  (on-activate* [_ ns-symbol])
  #_Release
  #_IDeref)

