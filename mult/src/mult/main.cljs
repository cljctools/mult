(ns mult.main
  (:require
   [clojure.core.async :as a :refer [<! >!  chan go alt! take! put! offer! poll! alts! pub sub
                                     timeout close! to-chan go-loop pipeline pipeline-async]]
   [goog.string :refer [format]]
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