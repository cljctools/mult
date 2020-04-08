(ns mult.impl.editor
  (:require
   [clojure.core.async :as a :refer [chan go go-loop <! >!  take! put! offer! poll! alt! alts! close!
                                     pub sub unsub mult tap untap mix admix unmix
                                     timeout to-chan  sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [goog.string :refer [format]]
   [goog.object]
   [clojure.string :as string]
   [cljs.reader :refer [read-string]]
   ["fs" :as fs]
   ["path" :as path]

   [mult.protocols.editor| :as p.editor|]
   [mult.protocols.ops| :as p.ops|]
   [mult.protocols.tab :as p.tab]
   [mult.protocols.channels :as p.channels]
   [mult.protocols.main| :as p.main|]
   [mult.impl.channels :as channels]))

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
                     (.join path context.extensionPath "resources/out/tabapp.js")
                     (vscode.Uri.file o)
                     (.asWebviewUri panel.webview o)
                     (.toString o))
        html (as-> nil o
               (.join path context.extensionPath "resources/index.html")
               (.readFileSync fs o)
               (.toString o)
               (string/replace o "/out/tabapp.js" script-uri))]
    html))

(defn read-workspace-file
  [filepath callback]
  (let []
    (as-> nil o
      (.join path vscode.workspace.rootPath filepath)
      (vscode.Uri.file o)
      (.readFile vscode.workspace.fs o)
      (.then o callback))))

(defn make-panel
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
    (.onDidReceiveMessage  panel.webview (fn [msg]
                                           (let [data (read-string msg)]
                                             ((:on-message handlers) id data))))
    (set! panel.webview.html html)
    panel))

; https://stackoverflow.com/a/41029103/10589291

(defn js-lookup
  [obj]
  (-> (fn [result key]
        (let [v (goog.object/get obj key)]
          (if (= "function" (goog/typeOf v))
            result
            (assoc result key v))))
      (reduce {} (.getKeys goog/object obj))))

(defn js-lookup-nested
  [obj]
  (if (goog.isObject obj)
    (-> (fn [result key]
          (let [v (goog.object/get obj key)]
            (if (= "function" (goog/typeOf v))
              result
              (assoc result key (js-lookup-nested v)))))
        (reduce {} (.getKeys goog/object obj)))
    obj))

(comment

  vscode.workspace.rootPath

  vscode.workspace.workspaceFile
  vscode.workspace.workspaceFolders

  (as-> nil o
    (.join path vscode.workspace.rootPath ".vscode")
    (vscode.Uri.file o)
    (.readDirectory vscode.workspace.fs o)
    (.then o (fn [d] (println d))))

  (as-> nil o
    (.join path vscode.workspace.rootPath ".vscode/mult.edn")
    (vscode.Uri.file o)
    (.readFile vscode.workspace.fs o)
    (.then o (fn [d] (println d))))

  ; https://code.visualstudio.com/api/references/vscode-api#TextDocument
  ; https://code.visualstudio.com/api/references/vscode-api#Selection
  ; https://code.visualstudio.com/api/references/vscode-api#Range
  
  (if  vscode.window.activeTextEditor
    vscode.window.activeTextEditor.document.uri
    :no-active-editor)

  (println vscode.window.activeTextEditor.selection)

  (js-lookup-nested vscode.window.activeTextEditor.selection)

  (do
    (def start vscode.window.activeTextEditor.selection.start)
    (def end vscode.window.activeTextEditor.selection.end)
    (def range (vscode.Range. start end))
    (def text (.getText vscode.window.activeTextEditor.document range)))
  
  ;;
  )

(defn proc-editor
  [channels ctx]
  (let [pid [:proc-editor (random-uuid)]
        {:keys [main| log| editor|m ops| cmd|]} channels
        {:keys [editor-context]} ctx
        proc| (chan 1)
        editor|t (tap editor|m (chan 10))
        editor|i (channels/editor|i)
        main|i (channels/main|i)
        ops|i (channels/ops|i)
        cmd|i (channels/cmd|i)
        log|i (channels/log|i)
        log (fn [& args] (put! log| (apply p.channels/-log log|i args)))
        release! #(do
                    (untap editor|m  editor|t)
                    (close! editor|t)
                    (close! proc|)
                    (put! main| (p.main|/-proc-stopped main|i pid)))]
    (put! main| (p.main|/-proc-started main|i pid proc|))
    (go (loop []
          (try
            (if-let [[v port] (alts! [editor|t proc|])]
              (condp = port
                proc| (release!)
                editor|t (let [op (p.channels/-op editor|i v)]
                           (condp = op
                             (p.editor|/-op-register-commands editor|i) (let [{:keys [commands]} v
                                                                              cmd-fn (fn [id args]
                                                                                       (put! cmd| (p.channels/-cmd cmd|i id args)))]
                                                                          (register-commands commands vscode editor-context cmd-fn))
                             (p.editor|/-op-show-info-msg editor|i) (let [{:keys [msg]} v]
                                                                      (show-information-message vscode msg))
                             (p.editor|/-op-create-tab editor|i) (let [{:keys [tab/id] :or {id (random-uuid)}} v
                                                                       panel (make-panel vscode editor-context id
                                                                                         {:on-message
                                                                                          (fn [id data]
                                                                                            (put! ops| (assoc data :tab/id id)))
                                                                                          :on-dispose
                                                                                          (fn [id]
                                                                                            (println "; tab onDispose " id)
                                                                                            (put! ops| (p.ops|/-tab-disposed ops|i id)))})
                                                                       tab (with-meta {:id id}
                                                                             {`p.tab/-put! (fn [_ v]
                                                                                             (.postMessage (.-webview panel) (pr-str v)))
                                                                              `p.tab/-dispose (fn [_] (println "dispose not implemented yet"))})]
                                                                   (>! ops| (p.ops|/-tab-created ops|i tab))
                                                                   (recur))

                             (p.editor|/-op-read-conf editor|i) (let [{:keys [filepath out|]} v
                                                                      cb #(put! out| (p.ops|/-read-conf-result ops|i (-> % (.toString) (read-string)) v))]
                                                                  (read-workspace-file filepath cb)))
                           (recur))))
            (catch js/Error e (do (log "; proc-editor error, will exit" e)))
            (finally
              (release!))))
        (println "; proc-editor go-block exits"))))

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