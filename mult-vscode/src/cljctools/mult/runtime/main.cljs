(ns cljctools.mult.runtime.main
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

   [cljctools.mult.protocols :as mult.protocols]
   [cljctools.mult.spec :as mult.spec]
   [cljctools.mult.core :as mult.core]
   [cljctools.mult.runtime.editor :as mult.runtime.editor]
   [cljctools.mult.runtime.edit :as mult.runtime.edit]

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
  (let [editor (mult.runtime.editor/create-editor context {})]
    ; commands should be registered before activation function returns
    (let [cmds {::mult.spec/cmd-open {::mult.runtime.editor/cmd-id "cljctools.mult.spec/cmd-open"}
                ::mult.spec/cmd-ping {::mult.runtime.editor/cmd-id "cljctools.mult.spec/cmd-ping"}
                ::mult.spec/cmd-eval {::mult.runtime.editor/cmd-id "cljctools.mult.spec/cmd-eval"}}]
      (doseq [k (keys cmds)] (s/assert ::mult.spec/mult-cmd k))
      (mult.runtime.editor/register-commands*
       editor
       {::mult.runtime.editor/cmds cmds
        ::mult.spec/cmd| (::mult.spec/cmd| @editor)}))
    (let [cmds {::mult.spec/cmd-format-current-form {::mult.runtime.editor/cmd-id "cljctools.mult.spec/cmd-format-current-form"}
                ::mult.spec/cmd-select-current-form {::mult.runtime.editor/cmd-id "cljctools.mult.spec/cmd-select-current-form"}}]
      (doseq [k (keys cmds)] (s/assert ::mult.spec/edit-cmd k))
      (mult.runtime.editor/register-commands*
       editor
       {::mult.runtime.editor/cmds cmds
        ::mult.spec/cmd| (::mult.spec/cmd| @editor)}))
    (go
      (let [config (<! (mult.protocols/read-mult-edn* editor))
            edit (mult.runtime.edit/create context {})
            cljctools-mult (mult.core/create {::mult.spec/config config
                                              ::mult.spec/edit edit
                                              ::mult.spec/editor editor})]
        (swap! registryA merge {::editor editor
                                ::cljctools-mult cljctools-mult
                                ::edit edit})

        (tap (::mult.spec/cmd|mult @editor) (::mult.spec/cmd| @cljctools-mult))
        (tap (::mult.spec/evt|mult @editor) (::mult.spec/ops| @cljctools-mult))
        (tap (::mult.spec/cmd|mult @editor) (::mult.spec/cmd| @edit))
        (tap (::mult.spec/evt|mult @editor) (::mult.spec/ops| @edit))))))

(defn deactivate
  []
  (go
    (let [{:keys [::editor ::cljctools-mult ::edit]} @registryA]
      (when (and editor cljctools-mult edit)
        (mult.protocols/release* cljctools-mult)
        (mult.protocols/release* edit)
        (mult.protocols/release* editor)
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