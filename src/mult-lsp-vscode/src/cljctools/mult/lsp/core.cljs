(ns cljctools.mult.lsp.core
  (:require
   [clojure.core.async :as a :refer [chan go go-loop <! >! take! put! offer! poll! alt! alts! close!
                                     pub sub unsub mult tap untap mix admix unmix pipe
                                     timeout to-chan  sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [clojure.string]
   [clojure.pprint :refer [pprint]]
   [cljs.reader :refer [read-string]]
   [goog.string.format]
   [goog.string :refer [format]]
   [clojure.spec.alpha :as s]))

(def fs (js/require "fs"))
(def path (js/require "path"))
(def vscode (js/require "vscode"))
(def lsp (js/require "vscode-languageclient"))

(s/def ::id keyword?)

(s/def ::create-opts (s/keys :req [::id]
                             :opt []))


(def jar-event-emitter (vscode.EventEmitter.))
(def contents-request (lsp.RequestType. "clojure/dependencyContents"))
(def clientA (atom nil))


(defn activate
  [context]
  (go
    (println ::activate)
    (let [server-options
          {:run {:command "bash" :args ["-c" "clojure-lsp"]}
           :debug {:command "bash" :args ["-c" "clojure-lsp"]}}

          client-options
          {:documentSelector [{:scheme "file" :language "clojure"}]
           :synchronize {:configurationSection "clojure-lsp"
                         :fileEvents (.. vscode -workspace (createFileSystemWatcher "**/.clientrc"))}
           :initializationOptions {"dependency-scheme" "jar"}}


          client (lsp.LanguageClient.
                  "clojure-lsp"
                  "Clojure Language Client"
                  (clj->js server-options)
                  (clj->js client-options))]

      (.. context -subscriptions (push (.start client)))
      (reset! clientA client))))

(defn deactivate
  []
  (go
    (println ::deactivate)
    (when-let [client @clientA]
      (.stop client)
      (reset! clientA nil))))

(def exports #js {:activate (fn [context]
                              (println ::activate)
                              (activate context))
                  :deactivate (fn []
                                (println ::deactivate)
                                (deactivate))})

(when (exists? js/module)
  (set! js/module.exports exports))

(defn ^:export main [] (println ::main))