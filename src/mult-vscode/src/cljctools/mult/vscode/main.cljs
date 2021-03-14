(ns cljctools.mult.vscode.main
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

   [cljctools.socket.spec :as socket.spec]
   [cljctools.socket.core :as socket.core]
   [cljctools.socket.nodejs-net.core :as socket.nodejs-net.core]

   [cljctools.mult.protocols :as mult.protocols]
   [cljctools.mult.spec :as mult.spec]
   [cljctools.mult.core :as mult.core]))

(def fs (js/require "fs"))
(def path (js/require "path"))
(def vscode (js/require "vscode"))

(do (clojure.spec.alpha/check-asserts true))

(s/def ::id keyword?)

(s/def ::create-opts (s/keys :req [::id]
                             :opt []))

(s/def ::tab-html-filepath string?)
(s/def ::tab-html-replacements (s/map-of string? string?))
(s/def ::tab-view-column some?)
(s/def ::on-tab-state-change ifn?)

(s/def ::create-tab-opts (s/keys :req [::mult.spec/tab-id]
                                 :opt [::mult.spec/tab-title]))

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
(s/def ::cmds (s/map-of ::mult.spec/cmd ::cmd))

(s/def ::register-commands-opts (s/keys :req [::cmds
                                              ::mult.spec/cmd|]
                                        :opt []))

(defprotocol Vscode
  (register-commands* [_ opts]))

(declare create-editor
         create-tab
         register-commands
         create-webview-panel)

(defonce ^:private registryA (atom {}))


(defn activate
  [context]
  (go
    (let [editor (create-editor context {::id ::editor})
          config (<! (mult.protocols/read-mult-edn* editor))
          cljctools-mult (mult.core/create {::mult.core/id ::mult
                                            ::mult.spec/config config
                                            ::mult.spec/editor editor
                                            ::socket.spec/create-opts-net-socket socket.nodejs-net.core/create-opts})]
      (register-commands* editor {::cmds {::mult.spec/cmd-open {::cmd-id "mult.open"}
                                          ::mult.spec/cmd-ping {::cmd-id "mult.ping"}
                                          ::mult.spec/cmd-eval {::cmd-id "mult.eval"}}
                                  ::mult.spec/cmd| (::mult.spec/cmd| @editor)})
      (pipe (::mult.spec/cmd| @editor)  (::mult.spec/cmd| @cljctools-mult))
      (pipe (::mult.spec/op| @editor)  (::mult.spec/op| @cljctools-mult))
      (swap! registryA assoc ::editor editor))))

(defn deactivate
  []
  (go
    (when-let [editor (get @registryA ::editor)]
      (mult.core/release ::mult)
      (mult.protocols/release* editor)
      (swap! registryA dissoc ::editor))))


(def exports #js {:activate (fn [context]
                              (println ::activate)
                              (activate context))
                  :deactivate (fn []
                                (println ::deactivate)
                                (deactivate))})

(when (exists? js/module)
  (set! js/module.exports exports))

(defn ^:export main [] (println ::main))

(defn create-editor
  [context
   {:as opts
    :keys [::id]}]
  {:pre [(s/assert ::create-opts opts)]
   :post [(s/assert ::mult.spec/editor %)]}
  (let [stateA (atom nil)

        cmd| (chan 10)

        op| (chan (sliding-buffer 10))

        active-text-editor
        ^{:type ::mult.spec/text-editor}
        (reify
          mult.protocols/TextEditor
          (text*
            [_]
            (when-let [vscode-active-text-editor (.. vscode -window -activeTextEditor)]
              (.getText (.. vscode-active-text-editor -document))))
          (text*
            [_ range]
            (when-let [vscode-active-text-editor (.. vscode -window -activeTextEditor)]
              (let [[line-start col-start line-end col-end] range
                    vscode-range (vscode.Range.
                                  (vscode.Position. line-start col-start)
                                  (vscode.Position. line-end col-end))]
                (.getText (.. vscode-active-text-editor -document) vscode-range))))

          (selection*
            [_]
            (when-let [vscode-active-text-editor (.. vscode -window -activeTextEditor)]
              (let [start (.. vscode-active-text-editor -selection -start)
                    end (.. vscode-active-text-editor -selection -end)
                    range (vscode.Range. start end)
                    text (.getText (.. vscode-active-text-editor -document) range)]
                text)))

          (filepath*
            [_]
            (when-let [vscode-active-text-editor (.. vscode -window -activeTextEditor)]
              (.. vscode-active-text-editor -document -fileName))))

        editor
        ^{:type ::mult.spec/editor}
        (reify
          mult.protocols/Editor
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

          mult.protocols/Release
          (release*
            [_]
            (close! cmd|))

          Vscode
          (register-commands*
            [_ opts]
            (register-commands context opts))

          cljs.core/IDeref
          (-deref [_] @stateA))]

    (do
      (.onDidChangeActiveTextEditor (.-window vscode) (fn [text-editor]
                                                        (put! op| {:op ::mult.spec/op-did-change-active-text-editor}))))
    (reset! stateA (merge
                    opts
                    {::opts opts
                     ::mult.spec/cmd| cmd|
                     ::mult.spec/op| op|}))
    editor))

(defn create-tab
  [context opts]
  {:pre [(s/assert ::create-tab-opts opts)]
   :post [(s/assert ::mult.spec/tab %)]}
  (let [stateA (atom {})

        tab
        ^{:type ::mult.spec/tab}
        (reify
          mult.protocols/Tab

          mult.protocols/Open
          (open*
            [_]
            (when-not (get @stateA ::panel)
              (let [on-tab-closed
                    (fn on-tab-closed
                      []
                      (when (::mult.spec/on-tab-closed opts)
                        ((::mult.spec/on-tab-closed opts)))
                      (mult.protocols/release* _))
                    panel (create-webview-panel context
                                                (merge
                                                 opts
                                                 {::mult.spec/on-tab-closed on-tab-closed}))]
                (swap! stateA assoc ::panel panel))))

          mult.protocols/Close
          (close*
            [_]
            (when-let [panel (get @stateA ::panel)]
              (.dispose panel)
              (swap! stateA dissoc ::panel)))
          mult.protocols/Send
          (send*
            [_ data]
            (when-let [panel (get @stateA ::panel)]
              (.postMessage (.-webview panel) data)))
          mult.protocols/Active?
          (active?*
            [_]
            (when-let [panel (get @stateA ::panel)]
              (.-active panel)))
          mult.protocols/Release
          (release*
            [_]
            (mult.protocols/close* _))
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
           ::mult.spec/cmd|]}]
  {:pre [(s/assert ::register-commands-opts opts)]}
  (doseq [[mult-cmd {:keys [::cmd-id] :as cmd}] cmds]
    (let [disposable (.. vscode.commands
                         (registerCommand
                          cmd-id
                          (fn [& args]
                            (put! cmd| {:op mult-cmd}))))]
      (.. context.subscriptions (push disposable)))))

(defn create-webview-panel
  [context
    {:as opts
    :keys [::mult.spec/tab-id
           ::mult.spec/tab-title
           ::mult.spec/on-tab-closed
           ::mult.spec/on-tab-message
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


