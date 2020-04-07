(ns mult.impl.editor
  (:require
   [clojure.core.async :as a :refer [chan go go-loop <! >!  take! put! offer! poll! alt! alts! close!
                                     pub sub unsub mult tap untap mix admix unmix
                                     timeout to-chan  sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [goog.string :refer [format]]
   [clojure.string :as string]
   [cljs.reader :refer [read-string]]
   ["fs" :as fs]
   ["path" :as path]

   [mult.protocols.proc| :as p.proc|]
   [mult.protocols.editor| :as p.editor|]
   [mult.protocols.channels :as p.channels]
   [mult.impl.channels :as channels]
   ))

(def vscode (js/require "vscode"))

(defn show-information-message
  [vscode msg]
  (.. vscode.window (showInformationMessage msg)))

(defn register-command
  [vscode context id callback]
  (let [disposable (.. vscode.commands
                       (registerCommand
                        id
                        (fn [& args]
                          (callback id args))))]
    (.. context.subscriptions (push disposable))))

(defn register-commands
  [commands vscode context callback]
  (let [ids commands]
    (doseq [id ids]
      (register-command vscode context id callback))))

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
  [vscode context id handlers]
  (let [panel (vscode.window.createWebviewPanel
               id
               "mult tab"
               vscode.ViewColumn.Two
               #js {:enableScripts true
                    :retainContextWhenHidden true})
        html (tabapp-html vscode context panel)]
    (.onDidDispose panel (fn []
                           ((:on-dispose handlers) id)))
    (.onDidReceiveMessage  panel.webview (fn [data]
                                           (let [msg (read-string data)]
                                             ((:on-message handlers) id msg))))
    (set! panel.webview.html html)
    panel))

(defn forward-to-tabapp
  [tab v]
  (let []
    (.postMessage (.-webview tab) (str v))))

(defn proc-editor
  [{channels :channels
    context :editor-context}]
  (let [{:keys [proc| log| editor| editor|p]} channels
        editor|i (channels/editor|i)
        proc|i (channels/proc|i)
        log|i (channels/log|i)
        editor|s (sub editor|p (p.editor|/-topic-editor-op editor|i) (chan 10))
        unsubscribe #(do
                       (unsub editor|p (p.editor|/-topic-editor-op editor|i) editor|s)
                       (close! editor|s))]
    (>! proc| (p.proc|/-started proc|i))
    (go (loop [state {:tabs {:current nil}}]
          (if-let [[v port] (alts! [proc| editor|s])]
            (condp = port
              proc| (condp = (:op v)
                      (p.proc|/-op-stop proc|i) (let [{:keys [out|]} v]
                                                  (unsubscribe)
                                                  (>! out| (p.proc|/-stopped proc|i))
                                                  (put! log| (p.channels/-info log|i nil "proc-editor exiting" nil))))
              editor|s (let [op (:op v)]
                         (condp = op
                           (p.editor|/-op-register-commands editor|i) (let [{:keys [commands]} v
                                                                            cmd-fn (fn [id args]
                                                                                     (put! editor| (p.editor|/-cmd editor|i id args)))]
                                                                        (register-commands commands vscode context cmd-fn))
                           (p.editor|/-op-show-info-msg editor|i) (let [{:keys [msg]} v]
                                                                    (show-information-message vscode msg))
                           (p.editor|/-op-open-repl-tab editor|i) (let [{:keys [tab/id] :or {id (random-uuid)}} v
                                                                        tab (make-tab vscode context id
                                                                                      {:on-message
                                                                                       (fn [id msg]
                                                                                         (put! editor| (p.editor|/-tab-on-message editor|i id msg)))
                                                                                       :on-dispose
                                                                                       (fn [id]
                                                                                         (put! editor| (p.editor|/-tab-disposed editor|i id)))})]
                                                                    (recur (-> state
                                                                               (update-in [:tabs] assoc id tab)
                                                                               (update-in [:tabs] assoc :current tab))))
                           (p.editor|/-op-tab-append editor|i) (forward-to-tabapp (get-in state [:tabs (:tab/id v)]) v)
                           (p.editor|/-op-tab-clear editor|i) (forward-to-tabapp (get-in state [:tabs (:tab/id v)]) v))
                         (recur state))))))))

#_(let [{:keys [op]} v]
    (println (format "; proc-ops %s" op))
    (condp = op
      :activate (let []
                  (register-commands (default-commands) editor-ctx cmd|))
      :deactivate (let []
                    (put! (channels :system|) {:ch/topic :system :proc/op :exit}))
      :tab/add (let [id (random-uuid)
                     tab (make-tab editor-ctx id ops|)]
                 (swap! state update-in [:tabs] assoc id tab)
                 (swap! state update-in [:tabs] assoc :current tab))
      :tab/on-dispose (let [{:keys [tab/id]} v]
                        (swap! state update-in [:tabs] dissoc id))
      :tab/on-message (let [{:keys [tab/msg]} v]
                        (println msg))
      :tab/send (let [{:keys [tab/id tab/msg]} v
                      tab (get-in @state [:tabs id])]
                  (.postMessage (.-webview tab) (str msg))))
    (recur))