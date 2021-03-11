(ns cljctools.mult.editor.impl
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
   [cljctools.mult.editor.protocols :as editor.protocols]
   [cljctools.mult.editor.spec :as editor.spec]))

(declare vscode
         path
         fs)

(when (exists? js/module)
  (def fs (js/require "fs"))
  (def path (js/require "path"))
  (def vscode (js/require "vscode"))
  #_(set! js/WebSocket ws)
  #_(set! js/module.exports exports))

;; (try
;;   (def vscode (node/require "vscode"))
;;   (catch js/Error e (do (println "Error on (node/require vscode)") (println e))))

(s/def ::context some?)


(s/def ::create-opts (s/and
                      ::editor.spec/create-opts
                      (s/keys :req [::context]
                              :opt [])))

(s/def ::tab-html-filepath string?)
(s/def ::tab-html-replacements (s/map-of string? string?))
(s/def ::tab-view-column some?)
(s/def ::on-tab-state-change ifn?)

(s/def ::create-tab-opts (s/and
                          ::editor.spec/create-tab-opts
                          (s/keys :req [::editor.spec/tab-id]
                                  :opt [::tab-html-filepath
                                        ::tab-html-replacements
                                        ::tab-view-column
                                        ::editor.spec/on-tab-closed
                                        ::editor.spec/on-tab-message
                                        ::on-tab-state-change])))

(def ^:const NS_DECL_LINE_RANGE 100)

(defn create
  [{:as opts
    :keys [::editor.spec/id
           ::context]}]
  {:pre [(s/assert ::create-opts opts)]}
  (let [stateA (atom nil)

        active-text-editor
        ^{:type ::editor.spec/text-editor}
        (reify
          editor.protocols/TextEditor
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
        ^{:type ::editor.spec/editor}
        (reify
          editor.protocols/Editor
          (show-notification*
            [_ text]
            (.. vscode.window (showInformationMessage text)))

          (register-commands*
            [_ {:as opts
                :keys [::editor.spec/cmd-ids
                       ::editor.spec/cmd|]}]
            {:pre [(s/assert ::editor.spec/register-commands-opts opts)]}
            (doseq [cmd-id cmd-ids]
              (let [disposable (.. vscode.commands
                                   (registerCommand
                                    cmd-id
                                    (fn [& args]
                                      (put! cmd| {::editor.spec/cmd-id cmd-id}))))]
                (.. context.subscriptions (push disposable)))))

          (active-text-editor*
            [_]
            active-text-editor)

          (create-tab*
            [_ {:as opts
                :keys [::editor.spec/tab-id
                       ::editor.spec/tab-title
                       ::editor.spec/on-tab-closed
                       ::editor.spec/on-tab-message

                       ::tab-view-column
                       ::tab-html-filepath
                       ::on-tab-state-change
                       ::tab-html-replacements]
                :or {tab-html-replacements {"./out/ui/main.js" "./resources/out/ui/main.js"
                                            "./css/style.css" "./css/style.css"}
                     tab-html-filepath "./resources/index.html"
                     tab-view-column vscode.ViewColumn.Two}}]
            {:pre [(s/assert ::create-tab-opts opts)]}
            (let [stateA (atom {})

                  tab
                  ^{:type ::editor.spec/tab}
                  (reify
                    editor.protocols/Tab

                    editor.protocols/Open
                    (open*
                      [_]
                      (when-not (get @stateA ::panel)
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
                                               (on-tab-closed _))))

                            (when on-tab-message
                              (.onDidReceiveMessage (.-webview panel)
                                                    (fn [msg]
                                                      (on-tab-message _ msg))))

                            (when on-tab-state-change
                              (.onDidChangeViewState panel
                                                     (fn [panel]
                                                       (on-tab-state-change _)))))
                          (set! (.-html (.-webview panel)) html)
                          (swap! stateA assoc ::panel panel))))

                    editor.protocols/Close
                    (close*
                      [_]
                      (when-let [panel (get @stateA ::panel)]
                        (.dispose panel)
                        (swap! stateA dissoc ::panel)))
                    editor.protocols/Send
                    (send*
                      [_ data]
                      (when-let [panel (get @stateA ::panel)]
                        (.postMessage (.-webview panel) data)))
                    editor.protocols/Active?
                    (active?*
                      [_]
                      (when-let [panel (get @stateA ::panel)]
                        (.-active panel)))
                    editor.protocols/Release
                    (release*
                      [_]
                      (editor.protocols/close* _))
                    cljs.core/IDeref
                    (-deref [_] @stateA))]
              (reset! stateA (merge
                              opts
                              {::opts opts
                               ::panel nil}))

              tab))

          editor.protocols/Release
          (release*
            [_]
            (do nil))

          cljs.core/IDeref
          (-deref [_] @stateA))]
    (reset! stateA (merge
                    opts
                    {::opts opts}))
    editor))