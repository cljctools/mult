(ns mult.impl.editor
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
   [cljs.reader :refer [read-string]]
   [bencode-cljc.core :refer [serialize deserialize]]
   [mult.protocol :refer [Ops| Tab Connection]]))

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

(defn register-commands
  [commands vscode context out|]
  (let [ids commands]
    (doseq [id ids]
      (register-command vscode context out| {:cmd/id id}))))

(defn tabapp-html
  [vscode context panel]
  (def panel panel)
  (let [script-uri (as-> nil o
                     (.join path context.extensionPath "resources/out/main.js")
                     (vscode.Uri.file o)
                     (.asWebviewUri panel.webview o)
                     (.toString o))
        html (as-> nil o
               (.join path context.extensionPath "resources/index.html")
               (.readFileSync fs o)
               (.toString o)
               (string/replace o "/out/main.js" script-uri))]
    html))

(defn make-tab
  [vscode context id ops|]
  (let [panel (vscode.window.createWebviewPanel
               id
               "mult tab"
               vscode.ViewColumn.Two
               #js {:enableScripts true
                    :retainContextWhenHidden true})
        html (tabapp-html vscode context panel)]
    (.onDidDispose panel (fn []
                           (put! ops| {:op :tab/on-dispose :tab/id id})))
    (.onDidReceiveMessage  panel.webview (fn [data]
                                           (let [msg (read-string data)]
                                             (put! ops| {:op :tab/on-message :tab/msg msg}))))
    (set! panel.webview.html html)
    panel))