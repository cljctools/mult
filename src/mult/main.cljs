(ns mult.main
  (:require
   [clojure.core.async :as a :refer [<! >!  chan go alt! take! put! offer! poll! alts! pub sub
                                     timeout close! to-chan go-loop pipeline pipeline-async]]
   [goog.string :refer [format]]
   ))

(def vscode (js/require "vscode"))

(comment 
  
  (js/console.log 3)
  (hello-fn)
  ;;
  )

(defn hello-fn []
  (.. vscode.window (showInformationMessage
                     (format "Hello World! %s" (type (chan 1)))
                     #_(str "Hello World!" (type (chan 1))))))

(defn activate
  [context]
  (.log js/console "; activate")
  (let [disposable (.. vscode.commands
                       (registerCommand
                        "mult.helloWorld"
                        #'hello-fn))]
    (.. context.subscriptions (push disposable))))

(defn deactivate []
  (.log js/console "; deactivate"))

(defn reload
  []
  (.log js/console "Reloading...")
  (js-delete js/require.cache (js/require.resolve "./main")))

(def exports #js {:activate activate
                  :deactivate deactivate})