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

   [cljctools.socket.spec :as socket.spec]
   [cljctools.socket.core :as socket.core]
   [cljctools.socket.nodejs-net.core :as socket.nodejs-net.core]

   [cljctools.mult.spec :as mult.protocols]
   [cljctools.mult.spec :as mult.spec]
   [cljctools.mult.core :as mult.core]))

(def fs (js/require "fs"))
(def path (js/require "path"))
(def vscode (js/require "vscode"))

(do (clojure.spec.alpha/check-asserts true))



(s/def ::id keyword?)
(s/def ::context some?)


(s/def ::create-opts (s/keys :req [::id
                                   ::context]
                             :opt []))

(s/def ::tab-html-filepath string?)
(s/def ::tab-html-replacements (s/map-of string? string?))
(s/def ::tab-view-column some?)
(s/def ::on-tab-state-change ifn?)

(s/def ::create-tab-opts (s/keys :req [::mult.spec/tab-id]
                                 :opt [::mult.spec/tab-title]))

(s/def ::create-webview-panel-opts (s/and
                                    ::create-tab-opts
                                    (s/keys :req [::context]
                                            :opt [::on-tab-closed
                                                  ::on-tab-message
                                                  ::on-tab-state-change
                                                  ::tab-html-filepath
                                                  ::tab-html-replacements
                                                  ::tab-view-column])))

(s/def ::cmd-id string?)
(s/def ::cmd-ids (s/coll-of ::cmd-id))
(s/def ::register-commands-opts (s/keys :req [::context
                                              ::cmd-ids
                                              ::mult.spec/cmd|]
                                        :opt []))

(defprotocol Vscode
  (register-commands* [_]))

(declare create-editor
         create-tab
         register-commands
         create-webview-panel)

(defonce ^:private registryA (atom {}))

(def ^:const NS_DECL_LINE_RANGE 100)

(defn activate
  [cotext]
  (go
    (let [editor (create-editor {::id ::editor
                                 ::context context})
          {:keys [::mult.spec/cmd|]} @editor
          mult (mult.core/create {::mult.core/id ::mult
                                  ::mult.spec/editor editor
                                  ::mult.spec/cmd| cmd|})]
      (register-commands* editor {::cmd-ids #{"mult.open"
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
  [{:as opts
    :keys [::id
           ::context]}]
  {:pre [(s/assert ::create-opts opts)]
   :post [(s/assert ::mult.spec/Editor %)]}
  (let [stateA (atom nil)

        cmd| (chan 10)

        active-text-editor
        ^{:type ::mult.spec/TextEditor}
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
            (create-tab opts))

          mult.protocols/Release
          (release*
            [_]
            (do nil))

          Vscode
          (register-commands*
            [_ opts]
            (register-commands (merge opts
                                      {::context context})))

          cljs.core/IDeref
          (-deref [_] @stateA))]

    (reset! stateA (merge
                    opts
                    {::opts opts
                     ::mult.spec/cmd| cmd|}))
    editor))

(defn create-tab
  [opts]
  {:pre [(s/assert ::create-tab-opts opts)]
   :post [(s/assert ::mult.spec/Tab %)]}
  (let [stateA (atom {})

        tab
        ^{:type ::mult.spec/Tab}
        (reify
          mult.protocols/Tab

          mult.protocols/Open
          (open*
            [_]
            (when-not (get @stateA ::panel)
              (let [panel (create-webview-panel (merge
                                                 {::context context}
                                                 opts))]
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
  [{:as opts
    :keys [::context
           ::cmd-ids
           ::cmd|]}]
  {:pre [(s/assert ::register-commands-opts opts)]}
  (doseq [cmd-id cmd-ids]
    (let [disposable (.. vscode.commands
                         (registerCommand
                          cmd-id
                          (fn [& args]
                            (put! cmd| {::cmd-id cmd-id}))))]
      (.. context.subscriptions (push disposable)))))

(defn create-webview-panel
  [{:as opts
    :keys [::context
           ::mult.spec/tab-id
           ::mult.spec/tab-title
           ::on-tab-closed
           ::on-tab-message
           ::on-tab-state-change
           ::tab-view-column
           ::tab-html-filepath
           ::tab-html-replacements]
    :or {tab-id (str #?(:clj  (java.util.UUID/randomUUID)
                        :cljs (random-uuid)))
         tab-title "Default title"
         tab-html-replacements {"./out/ui/main.js" "./resources/out/ui/main.js"
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