(ns mult.impl.conn
  (:require
   [clojure.core.async :as a :refer [<! >!  chan go alt! take! put! offer! poll! alts! pub sub
                                     timeout close! to-chan go-loop sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [goog.string :refer [format]]
   [clojure.string :as string]
   [clojure.pprint :refer [pprint]]
   ["fs" :as fs]
   ["path" :as path]
   ["net" :as net]
   ["bencode" :as bencode]
   ["nrepl-client" :as nrepl-cleint]
   [cljs.reader :refer [read-string]]
   [bencode-cljc.core :refer [serialize deserialize]]

   [mult.protocols.conn :as p.conn]))


(defn create
  [opts]
  (let []
    )
  )


(defn- create-nrepl
  [opts]
  (let [{:keys [port host out|]} opts
        socket (.connect nrepl-cleint #js {:port port
                                           :host host})
        
        
        ]
    (reify
      p.conn/Conn
      (-disconnect [_] (.end socket))
      p.conn/NRepl
      (-clone-session [_ opts])
      (-close-session [_ session-id opts])
      (-describe [_  opts])
      (-eval [_ code session-id opts])
      (-interrupt [_ session-id opts])
      (-load-file [_ file opts])
      (-ls-sessions [_])
      (-sideloader-provide [_ content name session-id type opts])
      (-sideloader-start [_ session-id opts])
      (-stdin [_ stdin-content opts]))))

(comment

  (do
    (def c (.connect nrepl-cleint #js {:port 8899
                                       :host "localhost"}))
    (doto c
      (.on "connect" (fn []
                       (println "; net/Socket connect")))
      (.on "ready" (fn []
                     (println "; net/Socket ready")))
      (.on "timeout" (fn []
                       (println "; net/Socket timeout")))
      (.on "close" (fn [hadError]
                     (println "; net/Socket close")
                     (println (format "hadError %s"  hadError))))
      (.on "error" (fn [e]
                     (println "; net/Socket error")
                     (println e)))))

  (.end c)


  (def code "shadow.cljs.devtools.api/compile")
  (.eval c "conj" (fn [err result]
                    (println ".eval data")
                    (println (or err result))))
  (.lsSessions c (fn [err data]
                   (println ".lsSessions data")
                   (println data)))

  (.describe c (fn [err data]
                 (println ".describe data")
                 (println messages)))


  ;;
  )