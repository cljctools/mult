(ns cljctools.mult.edit.core
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

   [rewrite-clj.zip :as z]
   [rewrite-clj.parser :as p]
   [rewrite-clj.node :as n]
   [rewrite-clj.paredit]
   [cljfmt.core]

   [cljctools.edit.spec :as edit.spec]
   [cljctools.edit.core :as edit.core]

   [cljctools.mult.edit.spec :as mult.edit.spec]
   [cljctools.mult.edit.protocols :as mult.edit.protocols]))

(def fs (js/require "fs"))
(def path (js/require "path"))
(def vscode (js/require "vscode"))

(declare
 register-formatter)

(s/def ::create-opts (s/keys :req []
                             :opt []))

(defn create
  [{:keys [::mult.edit.spec/clj-string] :as opts}]
  {:pre [(s/assert ::create-opts opts)]
   :post [(s/assert ::mult.edit.spec/edit %)]}
  (let [stateA (atom nil)
        ops| (chan 10)
        cmd| (chan 10)
        evt| (chan (sliding-buffer 10))
        evt|mult (mult evt|)

        zlocA (atom (z/of-string clj-string))

        edit
        ^{:type ::mult.edit.spec/edit}
        (reify
          mult.edit.protocols/Edit
          mult.edit.protocols/Release
          (release*
            [_]
            (close! ops|)
            (close! cmd|))
          cljs.core/IDeref
          (-deref [_] @stateA))]

    (reset! stateA (merge
                    opts
                    {:opts opts
                     ::zlocA zlocA
                     ::mult.edit.spec/ops| ops|
                     ::mult.edit.spec/cmd| cmd|
                     ::mult.edit.spec/evt| evt|
                     ::mult.edit.spec/evt|mult evt|mult}))

    (register-formatter)

    (.. vscode -window
        (onDidChangeActiveTextEditor
         (fn [text-editor]
           (when (and text-editor
                      (= "clojure" (.. text-editor -document -languageId)))
             (let [text (.. text-editor -document (getText))
                   cursor (.. text-editor -selection -active) ; is zero based
                   cursor-position [(. cursor -line) (. cursor -character)]]
               (put! ops| {:op ::mult.edit.spec/op-clj-string-changed
                           ::mult.edit.spec/clj-string text
                           ::mult.edit.spec/cursor-position cursor-position}))))))

    (go
      (loop []
        (let [[value port] (alts! [cmd| ops|])]
          (when value
            (condp = port

              cmd|
              (condp = (:op value)

                ::mult.edit.spec/cmd-format-current-form
                (let []
                  (println ::cmd-format-current-form))
                (do ::ignore-other-ops))

              ops|
              (do nil))
            (recur)))))
    edit))


(defn register-formatter
  []
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
                          (.. vscode -TextEdit (insert (.positionAt document 0) text-formatted))]))}))))