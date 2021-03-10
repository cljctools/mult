(ns cljctools.mult.editor
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
(s/def ::tab-id (s/or :uuid uuid? :string string?))
(s/def ::tab-title string?)
(s/def ::tab-html-filepath string?)
(s/def ::tab-html-replacements (s/map-of string? string?))
(s/def ::tab-view-column any?)
(s/def ::tab-active? boolean?)
(s/def ::tabs (s/map-of some? some?))
(s/def ::cmd-id string?)
(s/def ::cmd-ids (s/coll-of ::cmd-id))
(s/def ::info-msg string?)

(s/def ::filepath string?)
(s/def ::dirpath string?)
(s/def ::ns-symbol symbol?)
(s/def ::filenames (s/coll-of string?))
(s/def ::file-content string?)

(s/def ::tab-recv| some?)
(s/def ::tab-evt| some?)

(def ^:const NS_DECL_LINE_RANGE 100)

(defonce ^:private registryA (atom {}))
(defonce ^:private registry-tabsA (atom {}))

(declare show-information-message
         register-commands
         parse-ns
         active-ns
         open-tab
         close-tab
         tab-send)

(defn show-information-message
  [msg]
  (.. vscode.window (showInformationMessage msg)))

(defn register-commands
  [{:keys [::cmd-ids
           ::context
           ::cmd|] :as ops}]
  (doseq [cmd-id cmd-ids]
    (let [disposable (.. vscode.commands
                         (registerCommand
                          cmd-id
                          (fn [& args]
                            (put! cmd| {::cmd-id cmd-id}))))]
      (.. context.subscriptions (push disposable)))))

(defn parse-ns
  "Safely tries to read the first form from the source text.
   Returns ns name or nil"
  [filepath text]
  (try
    (when (re-matches #".+\.clj(s|c)?" filepath)
      (let [fform (read-string text)]
        (when (= (first fform) 'ns)
          (second fform))))
    (catch js/Error error (do
                            (println ::parse-ns filepath)
                            (println error)))))

(defn active-ns
  [active-text-editor]
  (when active-text-editor
    (let [range (vscode.Range.
                 (vscode.Position. 0 0)
                 (vscode.Position. NS_DECL_LINE_RANGE 0))
          text (.getText active-text-editor.document range)
          active-document-filepath active-text-editor.document.fileName
          ns-symbol (parse-ns active-document-filepath text)
          data {::filepath active-document-filepath
                ::ns-symbol ns-symbol}]
      data
      #_(prn active-text-editor.document.languageId))))

(defn open-tab
  [{:as opts
    :keys [::context
           ::tab-recv|
           ::tab-evt|
           ::tab-id
           ::tab-title
           ::tab-view-column
           ::tab-html-filepath
           ::tab-html-replacements]
    :or {tab-id (random-uuid)
         tab-recv| (chan 10)
         tab-evt| (chan 10)
         tab-title "Default title"
         tab-html-replacements {"./out/ui/main.js" "./resources/out/ui/main.js"
                                "./css/style.css" "./css/style.css"}
         tab-html-filepath "./resources/index.html"
         tab-view-column vscode.ViewColumn.Two}}]
  (go
    (when-not (get @registry-tabsA tab-id)
      (let [{:keys [on-message on-dispose on-state-change]
             :or {on-message (fn [msg]
                               (let [value (read-string msg)]
                                 (put! tab-recv| (merge value {::tab-id tab-id}))))
                  on-dispose (fn []
                               (println ::tab-disposed)
                               (swap! registry-tabsA dissoc tab-id)
                               (put! tab-evt| {:op ::onDidDispose
                                               ::tab-id tab-id}))
                  on-state-change (fn [data] (do nil))}} opts
            panel (vscode.window.createWebviewPanel
                   tab-id
                   tab-title
                   tab-view-column
                   #js {:enableScripts true
                        :retainContextWhenHidden true})
            _ (do (.onDidDispose panel (fn []
                                         (on-dispose)))
                  (.onDidReceiveMessage  (.-webview panel) (fn [msg]
                                                             (on-message msg)))
                  (.onDidChangeViewState panel (fn [panel]
                                                 (on-state-change {:op ::onDidChangeViewState
                                                                   ::tab-active? panel.active}))))
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
            stateA (atom (merge
                          opts
                          {::opts opts
                           ::panel panel
                           ::tab-recv| tab-recv|
                           ::tab-evt| tab-evt|
                           ::active? (fn [] panel.active)
                           ::close (fn [] (.dispose panel))
                           ::send (fn [value] (.postMessage (.-webview panel) (pr-str value)))}))]
        (set! (.-html (.-webview panel)) html)
        (swap! registry-tabsA assoc tab-id stateA)
        stateA))))

(defn close-tab
  [{:keys [::tab-id] :as opts}]
  (go
    (when (get @registry-tabsA tab-id)
      (let [state @(get @registry-tabsA tab-id)]
        ((::close state))
        (swap! registry-tabsA dissoc tab-id)))))

(defn tab-send
  [{:keys [::tab-id :value] :as opts}]
  (when (get @registry-tabsA tab-id)
    (let [state @(get @registry-tabsA tab-id)]
      ((::send state) value))))