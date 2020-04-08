(ns mult.protocols.lrepl)

(defprotocol LRepl
  (-eval [_ conn code]))

