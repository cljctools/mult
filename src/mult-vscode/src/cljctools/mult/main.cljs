(ns cljctools.mult.main
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

   [cljctools.mult.editor.protocols :as mult.editor.protocols]
   [cljctools.mult.editor.spec :as mult.editor.spec]
   [cljctools.mult.editor.core :as mult.editor.core]

   [cljctools.mult.format.protocols :as mult.format.protocols]
   [cljctools.mult.format.spec :as mult.format.spec]
   [cljctools.mult.format.core :as mult.format.core]

   [cljctools.mult.protocols :as mult.protocols]
   [cljctools.mult.spec :as mult.spec]
   [cljctools.mult.core :as mult.core]

   [cljfmt.core]))

(def fs (js/require "fs"))
(def path (js/require "path"))
(def vscode (js/require "vscode"))

(do (clojure.spec.alpha/check-asserts true))

(defonce ^:private registryA (atom {}))

(defn activate
  [context]
  (go
    (let [editor (mult.editor.core/create-editor context {::mult.editor.core/id ::editor})
          config (<! (mult.editor.protocols/read-mult-edn* editor))
          mult-format (mult.format.core/create {::mult.format.core/id ::mult-format
                                                ::mult.editor.spec/editor editor})
          cljctools-mult (mult.core/create {::mult.core/id ::mult
                                            ::mult.format.spec/mult-format mult-format
                                            ::mult.spec/config config
                                            ::mult.editor.spec/editor editor})]
      (.. vscode -languages
          (registerDocumentFormattingEditProvider
           "clojure"
           (clj->js {:provideDocumentFormattingEdits
                     (fn [document]
                       (let [text (.getText document)

                             text-formatted
                             (cljfmt.core/reformat-string
                              text
                              {:remove-consecutive-blank-lines? false})

                             range (vscode.Range.
                                    (.positionAt document 0)
                                    (.positionAt document (count text))
                                    #_(.positionAt document (- (count text) 1)))]
                         #js [(.. vscode -TextEdit (delete (.validateRange document range)))
                              (.. vscode -TextEdit (insert (.positionAt document 0) text-formatted))]))})))
      (mult.editor.core/register-commands*
       editor
       {::mult.editor.core/cmds {::mult.spec/cmd-open {::mult.editor.core/cmd-id ":cljctools.mult.spec/cmd-open"}
                                 ::mult.spec/cmd-ping {::mult.editor.core/cmd-id ":cljctools.mult.spec/cmd-ping"}
                                 ::mult.spec/cmd-eval {::mult.editor.core/cmd-id ":cljctools.mult.spec/cmd-eval"}}
        ::mult.editor.spec/cmd| (::mult.editor.spec/cmd| @editor)})
      (mult.editor.core/register-commands*
       editor
       {::mult.editor.core/cmds {::mult.format.spec/cmd-format-current-form {::mult.editor.core/cmd-id ":cljctools.mult.format.spec/format-current-form"}}
        ::mult.editor.spec/cmd| (::mult.editor.spec/cmd| @editor)})
      (tap (::mult.editor.spec/cmd|mult @editor) (::mult.spec/cmd| @cljctools-mult))
      (tap (::mult.editor.spec/evt|mult @editor) (::mult.spec/op| @cljctools-mult))
      (tap (::mult.editor.spec/cmd|mult @editor) (::mult.format.spec/cmd| @mult-format))
      (tap (::mult.editor.spec/evt|mult @editor) (::mult.format.spec/op| @mult-format))
      (swap! registryA assoc ::editor editor))))

(defn deactivate
  []
  (go
    (when-let [editor (get @registryA ::editor)]
      (mult.core/release ::mult)
      (mult.format.core/release ::mult-format)
      (mult.editor.protocols/release* editor)
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