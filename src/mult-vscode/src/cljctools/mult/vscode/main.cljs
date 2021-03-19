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

   [cljctools.mult.editor.protocols :as mult.editor.protocols]
   [cljctools.mult.editor.spec :as mult.editor.spec]
   [cljctools.mult.editor.core :as mult.editor.core]

   [cljctools.mult.fmt.protocols :as mult.fmt.protocols]
   [cljctools.mult.fmt.spec :as mult.fmt.spec]
   [cljctools.mult.fmt.core :as mult.fmt.core]

   [cljctools.mult.protocols :as mult.protocols]
   [cljctools.mult.spec :as mult.spec]
   [cljctools.mult.core :as mult.core]))

(def fs (js/require "fs"))
(def path (js/require "path"))
(def vscode (js/require "vscode"))

(do (clojure.spec.alpha/check-asserts true))

(defonce ^:private registryA (atom {}))

(defn activate
  [context]
  (go
    (let [editor (mult.editor.core/create-editor context {::id ::editor})
          config (<! (mult.editor.protocols/read-mult-edn* editor))
          fmt (mult.fmt.core/create {::mult.fmt.core/id ::mult-fmt
                                     ::mult.editor.spec/editor editor})
          cljctools-mult (mult.core/create {::mult.core/id ::mult
                                            ::mult.fmt.spec/fmt fmt
                                            ::mult.spec/config config
                                            ::mult.editor.spec/editor editor})]
      (mult.editor.core/register-commands*
       editor
       {::cmds {::mult.spec/cmd-open {::cmd-id ":cljctools.mult.spec/cmd-open"}
                ::mult.spec/cmd-ping {::cmd-id ":cljctools.mult.spec/cmd-ping"}
                ::mult.spec/cmd-eval {::cmd-id ":cljctools.mult.spec/cmd-eval"}}
        ::cmd| (::mult.editor.spec/cmd| @editor)})
      (mult.editor.core/register-commands*
       editor
       {::cmds {::mult.fmt.spec/cmd-format-current-form {::cmd-id ":cljctools.mult.fmt.spec/format-current-form"}}
        ::cmd| (::mult.editor.spec/cmd| @editor)})
      (tap (::mult.editor.spec/cmd|mult @editor) (::mult.spec/cmd| @cljctools-mult))
      (tap (::mult.editor.spec/evt|mult @editor) (::mult.spec/op| @cljctools-mult))
      (tap (::mult.editor.spec/cmd|mult @editor) (::mult.fmt.spec/cmd| @fmt))
      (tap (::mult.editor.spec/evt|mult @editor) (::mult.fmt.spec/op| @fmt))
      (swap! registryA assoc ::editor editor))))

(defn deactivate
  []
  (go
    (when-let [editor (get @registryA ::editor)]
      (mult.core/release ::mult)
      (mult.fmt.core/release ::mult-fmt)
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