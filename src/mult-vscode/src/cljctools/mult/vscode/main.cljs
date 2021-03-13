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

   [cljctools.cljs-self-hosting.spec :as cljs-self-hosting.spec]
   [cljctools.cljs-self-hosting.core :as cljs-self-hosting.core]
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


(s/def ::register-commands-opts (s/keys :req [::mult.spec/cmd-ids
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
    (let [editor (<! (create-editor context {::id ::editor}))
          {:keys [::mult.spec/cmd|]} @editor
          config-data (<! (mult.protocols/read-mult-edn* editor))
          config (clojure.walk/postwalk
                  (fn [form]
                    (if (and (list? form) (= (first form) 'fn))
                      (eval form)
                      form))  config-data)
          cljctools-mult (mult.core/create {::mult.core/id ::mult
                                            ::mult.spec/config config
                                            ::mult.spec/editor editor
                                            ::socket.spec/create-opts-net-socket socket.nodejs-net.core/create-opts
                                            ::mult.spec/cmd| cmd|})]
      (register-commands* editor {::mult.spec/cmd-ids #{"mult.open"
                                                        "mult.ping"
                                                        "mult.eval"}
                                  ::mult.spec/cmd| cmd|})
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
  {:pre [(s/assert ::create-opts opts)]}
  (let [stateA (atom nil)

        cmd| (chan 10)

        compiler (cljs-self-hosting.core/create-compiler)

        active-text-editor
        ^{:type ::mult.spec/text-editor}
        (reify
          mult.protocols/TextEditor
          (text*
            [_ range]
            (let [[line-start col-start line-end col-end] range
                  range (vscode.Range.
                         (vscode.Position. line-start col-start)
                         (vscode.Position. line-end col-end))]
              (.getText (.-activeTextEditor vscode) range)))

          (filepath*
            [_]
            (.-fileName (.-document (.-activeTextEditor vscode)))))

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
    (reset! stateA (merge
                    opts
                    {::opts opts
                     ::cljs-self-hosting.spec/compiler compiler
                     ::mult.spec/cmd| cmd|}))
    (go
      (<! (cljs-self-hosting.core/init
           compiler
           {:path (.join path (.-extensionPath context) "./resources/out/mult-bootstrap")
            :load-on-init '#{cljctools.mult.vscode.main
                             clojure.core.async}}))

      editor)))

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
              (let [panel (create-webview-panel context opts)]
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
    :keys [::mult.spec/cmd-ids
           ::mult.spec/cmd|]}]
  {:pre [(s/assert ::register-commands-opts opts)]}
  (doseq [cmd-id cmd-ids]
    (let [disposable (.. vscode.commands
                         (registerCommand
                          cmd-id
                          (fn [& args]
                            (put! cmd| {::mult.spec/cmd-id cmd-id}))))]
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





(comment

  (type '(println 3))
  (eval '(println 3))
  (type '(let [x 3]
           x))
  (eval '(let [x 3]
           x))

  (take! (cljs-self-hosting.core/eval-str
          (::cljs-self-hosting.spec/compiler  @(get @registryA ::editor))
          {::cljs-self-hosting.spec/code-str
           "
            (do
            
            [(cljs.core/type cljs.core/type)
            (type registryA)
            ]
            )
            
    "
           ::cljs-self-hosting.spec/ns-symbol
           'cljctools.mult.vscode.main})
         (fn [data]
           (prn data)
           (prn (type (:value data)))))


 ;;
  )