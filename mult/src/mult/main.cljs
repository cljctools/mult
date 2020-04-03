(ns mult.main
  (:require
   [clojure.core.async :as a :refer [<! >!  chan go alt! take! put! offer! poll! alts! pub sub
                                     timeout close! to-chan go-loop sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [goog.string :refer [format]]
   ["fs" :as fs]
   ["path" :as path]
   ["net" :as net]
   ["bencode" :as bencode]
   [bencode-cljc.core :refer [serialize deserialize]]
   [mult.vscode :as host]))

(declare  proc-ops)

(def channels (let []
                {:ch/system| (chan 10)
                 :ch/host-ops|  (chan 10)
                 :ch/cmd-in| (chan 10)
                 :ch/ops-in| (chan 10)}))

(defn activate
  [context]
  (host/proc-ops (select-keys channels [:ch/cmd-in| :ch/host-ops|]) context)
  (proc-ops (select-keys channels [:ch/cmd-in| :ch/host-ops|  :ch/ops-in|]))
  (put! (channels :ch/ops-in|) {:op :activate}))

(defn deactivate []
  (put! (channels :ch/ops-in|) {:op :deactivate}))

(defn reload
  []
  (.log js/console "Reloading...")
  (js-delete js/require.cache (js/require.resolve "./main")))

(def exports #js {:activate activate
                  :deactivate deactivate})

(defprotocol Connectable
  (-connect [_])
  (-disconnect [_]))

(defprotocol Evaluating
  (-eval-form [_]))

(defn proc-ops
  [{:keys [ch/cmd-in| ch/host-ops| ch/ops-in|]}]
  (go (loop []
        (if-let [[v port] (alts! [cmd-in| ops-in|])]
          (condp = port
            cmd-in| (let [{:keys [cmd/id cmd/args]} v]
                      (println (format "; cmd/id %s" id))
                      (condp = id
                        "mult.helloWorld" (do
                                            (>! host-ops|  {:op :show-information-message
                                                            :inforamtion-message "mult.helloWorld via channels"})
                                            (println "in 3sec will show another msg")
                                            (<! (timeout 3000))
                                            (>! host-ops|  {:op :show-information-message
                                                            :inforamtion-message "mult.helloWorld via channels, another message"}))
                        "mult.helloWorld2" (do
                                             (>! host-ops|  {:op :show-information-message
                                                             :inforamtion-message "mult.helloWorld2 via channels"}))))
            ops-in| (let [{:keys [op]} v]
                      (println (format "; op %s" op))
                      (condp = op
                        :activate (let []
                                    (>! host-ops|  {:op :register-default-commands}))
                        :deactivate (do
                                      (println "; deactivate"))))))
        (recur))))

(comment

  (offer! (channels :ch/host-ops|)  {:op :show-information-message
                                     :inforamtion-message "message via repl via channel"})

  ;;
  )

(def data$ (atom nil))

(defn on-data
  [buff]
  (println "; net/Socket data")
  (let [benstr (.toString buff)
        o (deserialize benstr)]
    (when (contains? o "value")
      (println o)
      (reset! data$ o))))

(comment



  (def ws (let [ws (net/Socket.)]
            (.connect ws #js {:port 5533 #_5511
                              :host "localhost"})
            (doto ws
              (.on "connect" (fn []
                               (println "; net/Socket connect")))
              (.on "ready" (fn []
                             (println "; net/Socket ready")))
              (.on "timeout" (fn []
                               (println "; net/Socket timeout")))
              (.on "close" (fn [hadError]
                             (println "; net/Socket close")
                             (println (format "hadError %s"  hadError))))
              (.on "data" (fn [buff] (on-data buff)))
              (.on "error" (fn [e]
                             (println "; net/Socket error")
                             (println e))))
            ws))

  (.write ws (str {:op "eval" :code "(+ 2 3)"}))
  (.write ws (str "error"))
  (dotimes [i 2]
    (.write ws (str {:op "eval" :code "(+ 2 3)"})))
  (dotimes [i 2]
    (.write ws (str "error")))


  (bencode/encode (str {:op "eval" :code "(+ 2 3)"}))
  (bencode/decode (bencode/encode (str {:op "eval" :code "(+ 2 3)"})))

  (.write ws (bencode/encode (str {:op "eval" :code "(+ 2 3)"})))


  (deserialize (serialize {:op "eval" :code "(+ 2 3)"}))

  ; clj only
  (binding [*ns* mult.vscode]
    [3 (type hello-fn)])

  (.write ws (serialize {:op "eval" :code "(+ 2 4)"}))

  (.write ws (serialize {:op "eval" :code "(do
                                           (in-ns 'abc.core)
                                           [(type foo) (foo)]
                                           )"}))



  ;;
  )