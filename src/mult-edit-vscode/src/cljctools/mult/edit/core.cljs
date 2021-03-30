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
   [rewrite-clj.node.protocols :as node]
   [rewrite-clj.zip.base :as base]
   [rewrite-clj.zip.move :as m]
   [rewrite-clj.custom-zipper.core :as zraw]
   [rewrite-clj.zip.findz]
   [rewrite-clj.paredit]
   [cljfmt.core]

   [cljctools.edit.spec :as edit.spec]
   [cljctools.edit.core :as edit.core]
   [cljctools.edit.db.core :as edit.db.core]

   [cljctools.mult.edit.spec :as mult.edit.spec]
   [cljctools.mult.edit.protocols :as mult.edit.protocols]))

(def fs (js/require "fs"))
(def path (js/require "path"))
(def vscode (js/require "vscode"))

(declare
 register-formatter
 register-keypress
 update-decorations)

(s/def ::create-opts (s/keys :req []
                             :opt []))

(s/def ::context some?)

(defn create
  [context {:keys [::mult.edit.spec/clj-string] :as opts}]
  {:pre [(s/assert ::create-opts opts)]
   :post [(s/assert ::mult.edit.spec/edit %)]}
  (let [stateA (atom nil)
        ops| (chan 10)
        cmd| (chan 10)
        evt| (chan (sliding-buffer 10))
        evt|mult (mult evt|)

        text-documentsA (atom {})

        create-text-document-state-fn
        (fn [text-document]
          (let [text (. text-document (getText))
                text-document-stateA
                (atom {::zloc (z/of-string text {:track-position? true})})]
            (swap! text-documentsA assoc text-document text-document-stateA)))
        
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
                     ::text-documentsA text-documentsA
                     ::mult.edit.spec/ops| ops|
                     ::mult.edit.spec/cmd| cmd|
                     ::mult.edit.spec/evt| evt|
                     ::mult.edit.spec/evt|mult evt|mult}))

    (register-formatter)
    (register-keypress context)

    #_(update-decorations (.. vscode -window -activeTextEditor))

    (.. vscode -window
        (onDidChangeActiveTextEditor
         (fn [text-editor]
           #_(println :change (.. text-editor -document -languageId))
           #_(when text-editor
               (println :change (.. text-editor -document -id)))
           #_(update-decorations text-editor))))

    (.. vscode -workspace
        (onDidChangeTextDocument
         (fn [text-document-event]
           (let [document (. text-document-event -document)
                 content-changes (. text-document-event -contentChanges)]
             #_(println ::onDidChangeTextDocument)
             (do nil)))))

    (.. vscode -workspace
        (onDidOpenTextDocument
         (fn [text-document]
           (when (= "clojure" (. text-document -languageId))
             (println ::open (. text-document -fileName))
             (let [text-document-stateA
                   (create-text-document-state-fn text-document)])))))

    (.. vscode -workspace
        (onDidCloseTextDocument
         (fn [text-document]
           (when (= "clojure" (. text-document -languageId))
             (println ::close (. text-document -fileName))
             (swap! text-documentsA dissoc text-document)))))

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

                ::mult.edit.spec/cmd-select-current-form
                (when-let [text-editor (.. vscode -window -activeTextEditor)]
                  (when-not (get @text-documentsA (. text-editor -document))
                    (create-text-document-state-fn (. text-editor -document)))
                  (time
                   (let [text-document-stateA (get @text-documentsA (. text-editor -document))
                        ;; text (.. text-editor -document (getText))
                        ;; zloc (z/of-string text {:track-position? true})
                         zloc (get @text-document-stateA ::zloc)
                         cursor (.. text-editor -selection -active) ; is zero based
                         cursor-position [(inc (. cursor -line)) (inc (. cursor -character))]
                         p? (constantly true)

                         zloc-current
                         (->> (sequence
                               (comp
                                (take-while identity)
                                (take-while (complement m/end?))
                                (filter #(and (p? %)
                                              (rewrite-clj.zip.findz/position-in-range? % cursor-position))))
                               (iterate zraw/next zloc))
                              last)

                         [start end] (z/position-span zloc-current)

                         new-selection (vscode.Selection.
                                        (vscode.Position. (dec (first end)) (dec (second end)))
                                        (vscode.Position. (dec (first start)) (dec (second start))))]
                     (set! (.-selection text-editor) new-selection)
                     #_(do
                         (println cursor-position)
                         (println (z/string zloc-current))
                         (println (z/position-span zloc-current))))))

                (do ::ignore-other-ops))

              ops|
              (do nil))
            (recur)))))
    edit))




(defn register-keypress
  [context]
  (let [disposable
        (.. vscode -commands
            (registerCommand
             "type"
             (fn [args]

               (when (= (. args -text) "\n")
                 (println ::enter-pressed))

               (.. vscode -commands
                   (executeCommand
                    "default:type"
                    args
                    #_(clj->js {:text (. args -text)}))))))]
    (.. context -subscriptions (push disposable))))

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


(defn update-decorations
  [text-editor]
  (when (and text-editor
             (= "clojure" (.. text-editor -document -languageId))
             (not (.-multAlreadyOpened (. text-editor -document))))
    (println ::update-decorations)
    (let [text (.. text-editor -document (getText))
          zloc (z/of-string text {:track-position? true})

          decoration-type-keywords
          (.. vscode -window
              (createTextEditorDecorationType
               (clj->js
                {:color "#0278ae" #_"#07689f" #_"#3282b8" #_"#51c2d5"})))

          decoration-type-brackets-fn
          (fn [color]
            (.. vscode -window
                (createTextEditorDecorationType
                 (clj->js
                  {:color color}))))

          decoration-type-brackets
          [(decoration-type-brackets-fn "#2a9d8f")
           (decoration-type-brackets-fn "#e9c46a")
           (decoration-type-brackets-fn "#e76f51")
           (decoration-type-brackets-fn "#00b4d8")]

          node-typesA (atom {})
          tagsA (atom {})

          decoration-options-keywords
          (transient [])

          decoration-options-brackets
          (mapv
           #(transient [])
           decoration-type-brackets)

          stepA (atom 0)

          zlocs (sequence
                 (comp
                  (take-while identity)
                  (take-while (complement m/end?)))
                 (iterate m/next zloc))]
      (doseq [zloc-current zlocs]
        (swap! node-typesA update-in [(node/node-type (-> zloc-current z/node))] (fnil inc 0))
        (swap! tagsA update-in [(base/tag zloc-current)] (fnil inc 0))

        #_(println (-> zloc z/node n/keyword-node?))
        #_(= (base/tag zloc) :keyword)
        #_(println (z/string zloc))

        (when (= :keyword (node/node-type (-> zloc-current z/node)))
          (let [[start end] (z/position-span zloc-current)]
            (conj! decoration-options-keywords
                   {:range (vscode.Range.
                            (dec (first start)) (dec (second start))
                            (dec (first end)) (dec (second end)))})))

        (when (= :seq (node/node-type (-> zloc-current z/node)))
          (let [[start end] (z/position-span zloc-current)
                decoration-options-bracket (get decoration-options-brackets (mod @stepA (count decoration-type-brackets)))]
            (swap! stepA inc)
            (conj! decoration-options-bracket
                   {:range (vscode.Range.
                            (dec (first start)) (dec (second start))
                            (dec (first start)) (second start))})
            (conj! decoration-options-bracket
                   {:range (vscode.Range.
                            (dec (first end)) (dec (second end))
                            (dec (first end)) (dec (dec (second end))))}))))

      #_(println @node-typesA)
      #_(println @tagsA)
      (.. text-editor
          (setDecorations
           decoration-type-keywords
           (clj->js (persistent! decoration-options-keywords))))
      (doseq [index (range 0 (count decoration-type-brackets))]
        (.. text-editor
            (setDecorations
             (get decoration-type-brackets index)
             (clj->js (persistent!  (get decoration-options-brackets index)))))))))