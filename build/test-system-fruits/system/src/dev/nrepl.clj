(ns dev.nrepl
  (:require
   [nrepl.server]
   [cider.nrepl :refer [cider-nrepl-handler]]
   [cider.piggieback]
   ;
   ))

(defprotocol NServer
  (-stop [_]))

(defn nserver-clj
  [{:keys [host port]}]
  (let [srv (nrepl.server/start-server
             :bind host
             :port port
             :handler cider-nrepl-handler #_(nrepl-handler)
             :middleware '[])]
    (println (str "; started nREPL clj on " host ":" port))
    (reify
      NServer
      (-stop [_] (nrepl.server/stop-server srv)))))

(defn nserver-cljs
  [{:keys [host port]}]
  (let [srv (nrepl.server/start-server
             :bind host
             :port port
             :handler (nrepl.server/default-handler #'cider.piggieback/wrap-cljs-repl)
             :middleware '[])]
    (println (str "; started nREPL cljs on " host ":" port))
    (reify
      NServer
      (-stop [_] (nrepl.server/stop-server srv)))))

