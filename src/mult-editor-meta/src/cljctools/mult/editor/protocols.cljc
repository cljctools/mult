(ns cljctools.mult.editor.protocols)

(defprotocol Release
  (release* [_]))

(defprotocol Editor
  (show-notification* [_ text])
  (register-commands* [_ opts])
  (active-text-editor* [_])
  (create-tab* [_ opts])
  #_Release
  #_IDeref)


(defprotocol TextEditor
  (text* [_] [_ range])
  (filepath* [_]))

(defprotocol Active?
  (active?* [_]))

(defprotocol Send
  (send* [_ msg]))

(defprotocol Open
  (open* [_]))

(defprotocol Close
  (close* [_]))

(defprotocol Tab
  #_Open
  #_Close
  #_Send
  #_Active?
  #_Release
  #_IDeref)

(defprotocol TabApp
  #_Send
  #_Release
  #_IDeref)