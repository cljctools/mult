(ns mult.protocols)

(defprotocol Procs
  ;; (-start [_ k])
  ;; (-stop [_ k])
  ;; (-restart [_ k])
  ;; (-up [_ ctx])
  ;; (-down [_])
  ;; (-downup [_ ctx])
  ;; (-up? [_])
  )

(defprotocol Proc
  (-start [_])
  (-start [_ c|])
  (-stop [_]))

(defprotocol Tab
  (-dispose [_]))

(defprotocol Connection
  (-connect [_])
  (-disconnect [_]))

(defprotocol StatusChan
  (-started [_])
  (-stopped [_]))



