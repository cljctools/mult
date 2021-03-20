(ns cljctools.mult.editor.core
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
   [clojure.spec.alpha :as s]

   [clojure.walk]

   [cljctools.mult.spec :as mult.spec]
   [cljctools.mult.format.spec :as mult.format.spec]

   [cljctools.mult.editor.protocols :as mult.editor.protocols]
   [cljctools.mult.editor.spec :as mult.editor.spec]))

(def fs (js/require "fs"))
(def path (js/require "path"))
(def vscode (js/require "vscode"))

(s/def ::id keyword?)

(s/def ::create-opts (s/keys :req [::id]
                             :opt []))

(s/def ::tab-html-filepath string?)
(s/def ::tab-html-replacements (s/map-of string? string?))
(s/def ::tab-view-column some?)
(s/def ::on-tab-state-change ifn?)

(s/def ::create-tab-opts (s/keys :req [::mult.editor.spec/tab-id]
                                 :opt [::mult.editor.spec/tab-title]))

(s/def ::vscode-text-editor (s/nilable some?))

(s/def ::create-text-editor-opts (s/keys :req []
                                         :opt [::vscode-text-editor]))

(s/def ::create-webview-panel-opts (s/and
                                    ::create-tab-opts
                                    (s/keys :req []
                                            :opt [::on-tab-closed
                                                  ::on-tab-message
                                                  ::on-tab-state-change
                                                  ::tab-html-filepath
                                                  ::tab-html-replacements
                                                  ::tab-view-column])))

(s/def ::cmd-id string?)
(s/def ::cmd (s/keys :req [::cmd-id]))

(s/def ::cmds (s/or
               :mult-cmds
               (s/map-of ::mult.spec/cmd ::cmd)

               :mult-format-cmds
               (s/map-of ::mult.format.spec/cmd ::cmd)))


(s/def ::register-commands-opts (s/keys :req [::cmds
                                              ::mult.editor.spec/cmd|]
                                        :opt []))


(defprotocol Editor
  (register-commands* [_ opts]))

(defprotocol TextEditor
  (set-vscode-text-editor* [_ text-editor]))

(declare
 create-text-editor
 create-tab
 create-webview-panel
 register-commands)

(defn create-editor
  [context
   {:as opts
    :keys [::id]}]
  {:pre [(s/assert ::create-opts opts)]
   :post [(s/assert ::mult.editor.spec/editor %)]}
  (let [stateA (atom nil)

        cmd| (chan 10)
        cmd|mult (mult cmd|)

        evt| (chan (sliding-buffer 10))
        evt|mult (mult evt|)

        active-text-editor
        (create-text-editor
         {::vscode-text-editor (.. vscode -window -activeTextEditor)})

        editor
        ^{:type ::mult.editor.spec/editor}
        (reify
          mult.editor.protocols/Editor
          (show-notification*
            [_ text]
            (.. vscode.window (showInformationMessage text)))

          (active-text-editor*
            [_]
            active-text-editor)

          (create-tab*
            [_ opts]
            (create-tab context opts))

          (read-mult-edn*
            [_]
            (go
              (let [workspace-file-uri (.-workspaceFile (.-workspace vscode))
                    workspace-file-path (.-fsPath workspace-file-uri)
                    workspace-edn (->
                                   (.readFileSync fs workspace-file-path)
                                   (.toString)
                                   (js/JSON.parse)
                                   (js->clj))
                    mult-edn-path-str (get-in workspace-edn ["settings" "cljctools.mult.edn"])
                    [folder-name filepath] (clojure.string/split mult-edn-path-str #":")
                    workspace-folder (first (filter
                                             (fn [folder] (= (.-name folder) folder-name))
                                             (.-workspaceFolders  (.-workspace vscode))))
                    mult-edn-path (.join path (.-fsPath (.-uri workspace-folder)) filepath)
                    mult-edn (->
                              (.readFileSync fs mult-edn-path)
                              (.toString)
                              (read-string))]
                mult-edn))
            #_(go
                (let [workspace-file-uri (.-workspaceFile (.-workspace vscode))
                      workspace-file-path (.-fsPath workspace-file-uri)
                      uint8array (<p! (.readFile (.-fs (.-workspace vscode)) workspace-file-uri))
                      text (.decode (js/TextDecoder.) uint8array)
                      json (js/JSON.parse text)
                      edn (js->clj json)]
                  (println text))))

          mult.editor.protocols/Release
          (release*
            [_]
            (close! cmd|))

          cljs.core/IDeref
          (-deref [_] @stateA)

          Editor
          (register-commands*
            [_ opts]
            (register-commands context opts)))]
    
    (do
      (.onDidChangeActiveTextEditor (.-window vscode) (fn [text-editor]
                                                        (set-vscode-text-editor* active-text-editor text-editor)
                                                        #_(set-vscode-text-editor* active-text-editor (.. vscode -window -activeTextEditor))
                                                        (put! evt| {:op ::mult.editor.spec/evt-did-change-active-text-editor}))))
    (reset! stateA (merge
                    opts
                    {::opts opts
                     ::mult.editor.spec/cmd| cmd|
                     ::mult.editor.spec/cmd|mult cmd|mult
                     ::mult.editor.spec/evt| evt|
                     ::mult.editor.spec/evt|mult evt|mult}))
    editor))


(defn create-text-editor
  [{:as opts
    :keys [::vscode-text-editor]}]
  {:pre [(s/assert ::create-text-editor-opts opts)]
   :post [(s/assert ::mult.editor.spec/text-editor %)]}
  (let [stateA (atom nil)

        text-editor
        ^{:type ::mult.editor.spec/text-editor}
        (reify
          mult.editor.protocols/TextEditor
          (text*
            [_]
            (when-let [vscode-active-text-editor (get @stateA ::vscode-text-editor)]
              (.getText (.. vscode-active-text-editor -document))))
          (text*
            [_ range]
            (when-let [vscode-active-text-editor (get @stateA ::vscode-text-editor)]
              (let [[line-start col-start line-end col-end] range
                    vscode-range (vscode.Range.
                                  (vscode.Position. line-start col-start)
                                  (vscode.Position. line-end col-end))]
                (.getText (.. vscode-active-text-editor -document) vscode-range))))

          (selection*
            [_]
            (when-let [vscode-active-text-editor (get @stateA ::vscode-text-editor)]
              (let [start (.. vscode-active-text-editor -selection -start)
                    end (.. vscode-active-text-editor -selection -end)
                    range (vscode.Range. start end)
                    text (.getText (.. vscode-active-text-editor -document) range)]
                text)))

          (filepath*
            [_]
            (when-let [vscode-active-text-editor (get @stateA ::vscode-text-editor)]
              (.. vscode-active-text-editor -document -fileName)))

          (replace*
            [_ text]
            (when-let [vscode-active-text-editor (get @stateA ::vscode-text-editor)]
              (let [document (.. vscode-active-text-editor -document)
                    full-text (mult.editor.protocols/text* _)
                    full-range (vscode.Range.
                                (.positionAt document 0)
                                (.positionAt document (count full-text))
                                #_(.positionAt document (- (count full-text) 1)))
                    result| (chan 1)]
                (->
                 (.edit vscode-active-text-editor
                        (fn [edit-builder]
                          (doto edit-builder
                            (.delete  (.validateRange document full-range))
                            (.insert (.positionAt document 0) text)
                            #_(.replace full-range text))))
                 (.then (fn [could-be-applied?]
                          (put! result| could-be-applied?)
                          (close! result|))))
                result|)))

          mult.editor.protocols/Release
          (release*
            [_]
            (do nil))
          cljs.core/IDeref
          (-deref [_] @stateA)

          TextEditor
          (set-vscode-text-editor*
            [_ text-editor]
            (swap! stateA assoc ::vscode-text-editor text-editor)))]

    (reset! stateA (merge
                    opts
                    {::opts opts}))
    text-editor))

(defn create-tab
  [context opts]
  {:pre [(s/assert ::create-tab-opts opts)]
   :post [(s/assert ::mult.editor.spec/tab %)]}
  (let [stateA (atom {})

        tab
        ^{:type ::mult.editor.spec/tab}
        (reify
          mult.editor.protocols/Tab

          mult.editor.protocols/Open
          (open*
            [_]
            (when-not (get @stateA ::panel)
              (let [on-tab-closed
                    (fn on-tab-closed
                      []
                      (when (::mult.editor.spec/on-tab-closed opts)
                        ((::mult.editor.spec/on-tab-closed opts)))
                      (mult.editor.protocols/release* _))
                    panel (create-webview-panel context
                                                (merge
                                                 opts
                                                 {::mult.editor.spec/on-tab-closed on-tab-closed}))]
                (swap! stateA assoc ::panel panel))))

          mult.editor.protocols/Close
          (close*
            [_]
            (when-let [panel (get @stateA ::panel)]
              (.dispose panel)
              (swap! stateA dissoc ::panel)))
          mult.editor.protocols/Send
          (send*
            [_ data]
            (when-let [panel (get @stateA ::panel)]
              (.postMessage (.-webview panel) data)))
          mult.editor.protocols/Active?
          (active?*
            [_]
            (when-let [panel (get @stateA ::panel)]
              (.-active panel)))
          mult.editor.protocols/Visible?
          (visible?*
            [_]
            (when-let [panel (get @stateA ::panel)]
              (.-visible panel)))
          mult.editor.protocols/Release
          (release*
            [_]
            (mult.editor.protocols/close* _))
          cljs.core/IDeref
          (-deref [_] @stateA))]
    (reset! stateA (merge
                    opts
                    {::opts opts
                     ::panel nil}))
    tab))

(defn register-commands
  [context
   {:as opts
    :keys [::cmds
           ::mult.editor.spec/cmd|]}]
  {:pre [(s/assert ::register-commands-opts opts)]}
  (doseq [[cmd-spec-key {:keys [::cmd-id] :as cmd}] cmds]
    (let [disposable (.. vscode.commands
                         (registerCommand
                          cmd-id
                          (fn [& args]
                            (put! cmd| {:op cmd-spec-key}))))]
      (.. context.subscriptions (push disposable)))))

(defn create-webview-panel
  [context
    {:as opts
    :keys [::mult.editor.spec/tab-id
           ::mult.editor.spec/tab-title
           ::mult.editor.spec/on-tab-closed
           ::mult.editor.spec/on-tab-message
           ::on-tab-state-change
           ::tab-view-column
           ::tab-html-filepath
           ::tab-html-replacements]
    :or {tab-id (str (random-uuid))
         tab-title "Default title"
         tab-html-replacements {"./out/mult-ui/main.js" "./resources/out/mult-ui/main.js"
                                "./css/style.css" "./css/style.css"}
         tab-html-filepath "./resources/index.html"
         tab-view-column vscode.ViewColumn.Two}}]
  {:pre [(s/assert ::create-webview-panel-opts opts)]}
  (let [panel (.createWebviewPanel (.-window vscode)
                                   tab-id
                                   tab-title
                                   tab-view-column
                                   #js {:enableScripts true
                                        :retainContextWhenHidden true})
        replacements-uris (into {}
                                (mapv (fn [[k filepath]]
                                        [k (as-> nil o
                                             (.join path (.-extensionPath context) filepath)
                                             (vscode.Uri.file o)
                                             (.asWebviewUri (.-webview panel) o)
                                             (.toString o))])
                                      tab-html-replacements))

        html (as-> nil o
               (.join path (.-extensionPath context) tab-html-filepath)
               (.readFileSync fs o)
               (.toString o)
               (reduce (fn [html [match replacement]]
                         (clojure.string/replace html match replacement)) o replacements-uris))]
    (do
      (when on-tab-closed
        (.onDidDispose panel
                       (fn []
                         (on-tab-closed))))

      (when on-tab-message
        (.onDidReceiveMessage (.-webview panel)
                              (fn [msg]
                                (on-tab-message msg))))

      (when on-tab-state-change
        (.onDidChangeViewState panel
                               (fn [panel]
                                 (on-tab-state-change)))))
    (set! (.-html (.-webview panel)) html)
    panel))


