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
   [clojure.spec.alpha :as s]))

(require '[cljctools.mult.editor :as mult.editor])

(when (exists? js/module)
  (def fs (js/require "fs"))
  (def path (js/require "path"))
  (def vscode (js/require "vscode"))
  #_(set! js/WebSocket ws)
  #_(set! js/module.exports exports))

;; (declare vscode)
;; (try
;;   (def vscode (node/require "vscode"))
;;   (catch js/Error e (do (println "Error on (node/require vscode)") (println e))))

(s/def ::context some?)
(s/def ::tab-html-filepath string?)
(s/def ::tab-html-replacements (s/map-of string? string?))
(s/def ::tab-view-column some?)

(s/def ::open-tab-opts (s/and
                        ::mult.editor/open-tab-opts
                        (s/keys :req [::mult.editor/tab-id]
                                :opt [::tab-html-filepath
                                      ::tab-html-replacements
                                      ::tab-view-column])))

(s/def ::create-opts (s/keys :req [::mult.editor/id
                                   ::context]
                             :opt []))

(def ^:const NS_DECL_LINE_RANGE 100)


(defonce ^:private registryA (atom {}))
(defonce ^:private registry-tabsA (atom {}))

(defn create
  [{:as opts
    :keys [::mult.editor/id
           ::context]}]
  {:pre [(s/assert ::create-opts opts)]}
  (or
   (get @registryA id)
   (let [stateA (atom nil)

         active-text-editor
         ^{:type ::mult.editor/text-editor}
         (reify
           mult.editor/TextEditor
           (text*
             [_ range]
             (let [[line-start col-start line-end col-end] range
                   range (vscode.Range.
                          (vscode.Position. line-start col-start)
                          (vscode.Position. line-end col-end))
                   (.getText (.-activeTextEditor vscode) range)]))

           (filepath*
             [_]
             (.-fileName (.-document (.-activeTextEditor vscode)))))

         editor
         ^{:type ::mult.editor/editor}
         (reify
           mult.editor/Editor
           (show-notification*
             [_ text]
             (.. vscode.window (showInformationMessage text)))

           (register-commands*
             [_ {:as ops
                 :keys [::mult.editor/cmd-ids
                        ::mult.editor/cmd|]}]
             {:pre [(s/assert ::mult.editor/register-commands-opts)]}
             (doseq [cmd-id cmd-ids]
               (let [disposable (.. vscode.commands
                                    (registerCommand
                                     cmd-id
                                     (fn [& args]
                                       (put! cmd| {::cmd-id cmd-id}))))]
                 (.. context.subscriptions (push disposable)))))

           (active-text-editor*
             [_]
             active-text-editor)

           (open-tab*
            [_ {:as opts
                :keys [::mult.editor/tab-id
                       ::mult.editor/tab-title
                       ::mult.editor/tab-recv|
                       ::mult.editor/tab-evt|
                       ::tab-view-column
                       ::tab-html-filepath
                       ::tab-html-replacements]
                :or {tab-recv| (chan 10)
                     tab-evt| (chan 10)
                     tab-title "Default title"
                     tab-html-replacements {"./out/ui/main.js" "./resources/out/ui/main.js"
                                            "./css/style.css" "./css/style.css"}
                     tab-html-filepath "./resources/index.html"
                     tab-view-column vscode.ViewColumn.Two}}]
            {:pre [(s/assert ::open-tab-opts)]}
            (let [stateA (atom nil)

                  panel (.createWebviewPanel vscode
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
                                   (clojure.string/replace html match replacement)) o replacements-uris))
                  tab
                  ^{:type ::mult.editor/tab}
                  (reify
                    mult.editor/Tab
                    mult.editor/Send
                    (send*
                      [_ data]
                      (.postMessage (.-webview panel) data))
                    mult.editor/Active?
                    (active?*
                      [_]
                      (.-active panel))
                    mult.editor/Release
                    (release*
                      [_]
                      (.dispose panel))
                    cljs.core/IDeref
                    (-deref [_] @stateA))]
              (reset! stateA (merge
                              opts
                              {::opts opts
                               ::panel panel}))
              (do (.onDidDispose panel (fn []
                                         (put! tab-evt| {:op ::onDidDispose
                                                         ::tab-id tab-id})))
                  (.onDidReceiveMessage  (.-webview panel) (fn [msg]
                                                             (let [value (read-string msg)]
                                                               (put! tab-recv| (merge value {::tab-id tab-id})))))
                  (.onDidChangeViewState panel (fn [panel]
                                                 (on-state-change {:op ::onDidChangeViewState
                                                                   ::tab-active? panel.active}))))

              (set! (.-html (.-webview panel)) html)
              tab))

           mult.editor/Release
           (release*
             [_]
             (do nil))

           cljs.core/IDeref
           (-deref [_] @stateA))]
     (reset! stateA (merge
                     opts
                     {::opts opts}))
     editor)))