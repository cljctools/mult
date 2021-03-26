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

   [cljctools.mult.edit.protocols :as mult.edit.protocols]
   [cljctools.mult.edit.spec :as mult.edit.spec]
   [cljctools.mult.edit.core :as mult.edit.core]

   [cljctools.mult.protocols :as mult.protocols]
   [cljctools.mult.spec :as mult.spec]
   [cljctools.mult.core :as mult.core]

   [cljfmt.core]))

(def fs (js/require "fs"))
(def path (js/require "path"))
(def vscode (js/require "vscode"))

(do (clojure.spec.alpha/check-asserts true))

#_(foo)
#_(foo)

(defonce ^:private registryA (atom {}))

(defn activate
  [context]
  (let [editor (mult.editor.core/create-editor context {})]
    ; commands should be registered before activation function returns
    (let [cmds {::mult.spec/cmd-open {::mult.editor.core/cmd-id "cljctools.mult.spec/cmd-open"}
                ::mult.spec/cmd-ping {::mult.editor.core/cmd-id "cljctools.mult.spec/cmd-ping"}
                ::mult.spec/cmd-eval {::mult.editor.core/cmd-id "cljctools.mult.spec/cmd-eval"}}]
      (doseq [k (keys cmds)] (s/assert ::mult.spec/cmd k))
      (mult.editor.core/register-commands*
       editor
       {::mult.editor.core/cmds cmds
        ::mult.editor.spec/cmd| (::mult.editor.spec/cmd| @editor)}))
    (let [cmds {::mult.edit.spec/cmd-format-current-form {::mult.editor.core/cmd-id "cljctools.mult.edit.spec/cmd-format-current-form"}
                ::mult.edit.spec/cmd-select-current-form {::mult.editor.core/cmd-id "cljctools.mult.edit.spec/cmd-select-current-form"}}]
      (doseq [k (keys cmds)] (s/assert ::mult.edit.spec/cmd k))
      (mult.editor.core/register-commands*
       editor
       {::mult.editor.core/cmds cmds
        ::mult.editor.spec/cmd| (::mult.editor.spec/cmd| @editor)}))
    (go
      (let [config (<! (mult.editor.protocols/read-mult-edn* editor))
            edit (mult.edit.core/create context {})
            cljctools-mult (mult.core/create {::mult.spec/config config
                                              ::mult.edit.spec/edit edit
                                              ::mult.editor.spec/editor editor})]
        (swap! registryA merge {::editor editor
                                ::cljctools-mult cljctools-mult
                                ::edit edit})

        (tap (::mult.editor.spec/cmd|mult @editor) (::mult.spec/cmd| @cljctools-mult))
        (tap (::mult.editor.spec/evt|mult @editor) (::mult.spec/ops| @cljctools-mult))
        (tap (::mult.editor.spec/cmd|mult @editor) (::mult.edit.spec/cmd| @edit))
        (tap (::mult.editor.spec/evt|mult @editor) (::mult.edit.spec/ops| @edit))))))

(defn deactivate
  []
  (go
    (let [{:keys [::editor ::cljctools-mult ::edit]} @registryA]
      (when (and editor cljctools-mult edit)
        (mult.protocols/release* cljctools-mult)
        (mult.edit.protocols/release* edit)
        (mult.editor.protocols/release* editor)
        (swap! registryA dissoc ::editor ::edit ::mult)))))

(def exports #js {:activate (fn [context]
                              (println ::activate)
                              (activate context))
                  :deactivate (fn []
                                (println ::deactivate)
                                (deactivate))})

(when (exists? js/module)
  (set! js/module.exports exports))

(defn ^:export main [] (println ::main))