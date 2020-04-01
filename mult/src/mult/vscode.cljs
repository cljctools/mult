(ns mult.vscode
  (:require
   [clojure.core.async :as a :refer [<! >!  chan go alt! take! put! offer! poll! alts! pub sub
                                     timeout close! to-chan go-loop pipeline pipeline-async]]
   [goog.string :refer [format]]))

(def vscode (js/require "vscode"))

(defn show-information-message
  [vscode msg]
  (.. vscode.window (showInformationMessage msg)))

(defn register-command
  [vscode context out| {:keys [cmd/id]}]
  (let [disposable (.. vscode.commands
                       (registerCommand
                        id
                        (fn [& args]
                          (put! out| {:cmd/id id
                                      :cmd/args args}))))]
    (.. context.subscriptions (push disposable))))

(defn register-default-commands
  [vscode context out|]
  (let [ids ["mult.helloWorld"
             "mult.helloWorld2"]]
    (doseq [id ids]
      (println id)
      (register-command vscode context out| {:cmd/id id}))))

(defn proc-ops
  [{:keys [ch/host-ops|  ch/cmd-in|]} context]
  (go (loop []
        (if-let [[v port] (alts! [host-ops|])]
          (condp = port
            host-ops|  (let [{:keys [op]} v]
                         (println (format "; op %s" op))
                         (condp = op
                           :register-command
                           (register-command  vscode context cmd-in| v)
                           :register-default-commands
                           (register-default-commands vscode context cmd-in|)
                           :show-information-message
                           (show-information-message vscode (:inforamtion-message v))))))
        (recur))))


(defn hello-fn []
  (.. vscode.window (showInformationMessage
                     (format "Hello World!!! %s" (type (chan 1)))
                     #_(str "Hello World!" (type (chan 1)))))
  #_(go
      (<! (timeout 3000))
      (put! (channels :ch/test|) {:op :show-info-msg})))

(comment

  (js/console.log 3)
  (js/console.log format)
  (go
    (<! (timeout 2000))
    (js/console.log (type format))
    (js/console.log (format "Hello World! %s" 123))

    (<! (timeout 1000))
    (js/console.log "done"))
  (hello-fn)
  ;;
  )