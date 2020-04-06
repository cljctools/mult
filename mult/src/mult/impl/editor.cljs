(ns mult.impl.editor
  (:require
   [clojure.core.async :as a :refer [<! >!  chan go alt! take! put! offer! poll! alts! pub sub
                                     timeout close! to-chan go-loop sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [goog.string :refer [format]]
   [clojure.string :as string]
   [cljs.reader :refer [read-string]]
   ["fs" :as fs]
   ["path" :as path]
   [mult.protocols.core]
   [mult.protocols.proc]
   [mult.protocols.editor :refer [Editor|]]
   [mult.impl.proc :refer [proc|-interface]]))

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
  [v]
  (let [{:keys [tab/id]} v
        tab (get-in state [:tabs id])]
    (.postMessage (.-webview tab) (str v))))

(defn editor|-interface
  []
  (let []
    (reify
      Editor|
      (-topic-editor-op [_] :editor/op)
      (-topic-extension-op [_] :extention/op)
      (-topic-editor-cmd [_] :editor/cmd)
      (-topic-tab [_] :tab/op)

      (-op-activate [_] :extention/activate)
      (-op-deactivate [_] :extention/deactivate)
      (-op-show-info-msg [_] :editor/show-info-msg)
      (-op-register-commands [_] :editor/register-commands)
      (-op-open-repl-tab [_] :editor/open-repl-tab)
      (-op-cmd [_] :editor/cmd)
      (-op-tab-clear [_] :tab/clear)
      (-op-tab-append [_] :tab/append)
      (-op-tab-disposed [_] :tab/disposed)

      (-activate [_ ctx] {:op (-op-activate _) :ctx ctx :topic (-topic-extension-op _)})
      (-deactivate [_] {:op (-op-deactivate _) :topic (-topic-extension-op _)})
      (-show-info-msg [_ msg] {:op (-op-show-info-msg _) :msg msg :topic (-topic-editor-op _)})
      (-register-commands [_ commands] {:op (-op-register-commands _) :commands commands :topic (-topic-editor-op _)})
      (-open-repl-tab [_ tab-id] {:op (-op-open-repl-tab _) :tab/id tab-id :topic (-topic-editor-op _)})
      (-cmd [_ id args] {:op (-op-cmd _) :cmd/id id :cmd/args args :topic (-topic-editor-op _)})
      (-tab-clear [_ id] {:op (-op-tab-clear _) :tab/id id  :topic (-topic-tab _)})
      (-tab-append [_ id data] {:op (-op-tab-append _) :tab/id id :data data :topic (-topic-tab _)})
      (-tab-disposed [_ id] {:op (-op-tab-disposed _) :tab/id id :topic (-topic-tab _)})
      (-tab-on-message [_ id msg] (merge msg {:tab/id id :topic (-topic-tab _)})))))

!@#$%^&*(){}|:<>?
p.proc|
(p.proc|/-op-stop proc|i a b c)
(p.proc-ch/-op-stop proc-ch-iface a b c)


(defn proc-editor
  [{channels :channels
    context :editor-context}]
  (let [{:keys [proc-chan log-chan editor-chan editor-pub]} channels
        editor-chan-iface (editor-chan-interface)
        proc-chan-iface (proc-chan-interface)
        log-chan-iface (log-chan-interface)
        editor-chan-sub (sub editor|p (-topic-editor-op editor|i) (chan 10))
        unsubscribe #(do
                       (unsub editor|p (-topic-editor-op editor|i) editor|s)
                       (close! editor|s))]
    (>! proc| (-started proc|i))
    (go (loop [state {:tabs {:current nil}}]
          (if-let [[v port] (alts! [proc| editor|s])]
            (condp = port
              proc-chan (condp = (:op v)
                          (-op-stop proc|i) (let [{:keys [out|]} v]
                                              (unsubscribe)
                                              (>! out| (-stopped proc|i))
                                              (put! log| (-info log|i nil "proc-editor exiting" nil))))
              editor|s (let [op (:op v)]
                         (condp = op
                           (-op-register-commands editor|i) (let [{:keys [commands]} v
                                                                  cmd-fn (fn [id args] (put! editor| (-cmd editor|i id args)))]
                                                              (register-commands vscode context cmd-fn))
                           (-op-show-info-msg editor|i) (let [{:keys [msg]} v]
                                                          (show-information-message vscode msg))
                           (-op-open-repl-tab editor|i) (let [{:keys [tab/id] :or {id (random-uuid)}} v
                                                              tab (make-tab context id {:on-message (fn [id msg] (-tab-on-message editor|i id msg))
                                                                                        :on-dispose (fn [id] (-tab-disposed editor|i id))})]
                                                          (recur (-> state
                                                                     (update-in [:tabs] assoc id tab)
                                                                     (update-in [:tabs] assoc :current tab))))
                           (-op-tab-append editor|i) (forward-to-tabapp v)
                           (-op-tab-clear editor|i) (forward-to-tabapp v))
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