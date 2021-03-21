(ns cljctools.mult.format.core
  (:require
   [clojure.core.async :as a :refer [chan go go-loop <! >! take! put! offer! poll! alt! alts! close!
                                     pub sub unsub mult tap untap mix admix unmix pipe
                                     timeout to-chan  sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [clojure.string]
   [clojure.pprint :refer [pprint]]
   #?(:cljs [cljs.reader :refer [read-string]])
   #?(:cljs [goog.string.format])
   #?(:cljs [goog.string :refer [format]])
   [clojure.spec.alpha :as s]

   [sci.core :as sci]

   [clojure.walk]

   [rewrite-clj.zip :as z]
   [rewrite-clj.parser :as p]
   [rewrite-clj.node :as n]
   [rewrite-clj.paredit]
   [cljfmt.core]

   [cljctools.mult.editor.spec :as mult.editor.spec]
   [cljctools.mult.editor.protocols :as mult.editor.protocols]
   [cljctools.mult.format.spec :as mult.format.spec]
   [cljctools.mult.format.protocols :as mult.format.protocols]))

(do (clojure.spec.alpha/check-asserts true))

(s/def ::id  (s/or :keyword keyword? :string string?))

(s/def ::create-opts (s/keys :req [::id
                                   ::mult.editor.spec/editor]
                             :opt []))


(defonce ^:private registryA (atom {}))

(declare text->ns-symbol
         send-data)

(defn create
  [{:keys [::id
           ::mult.editor.spec/editor] :as opts}]
  {:pre [(s/assert ::create-opts opts)]
   :post [(s/assert ::mult.format.spec/mult-format %)]}
  (let [stateA (atom nil)
        op| (chan 10)
        cmd| (chan 10)

        mult-format
        ^{:type ::mult.format.spec/mult-format}
        (reify
          mult.format.protocols/MultFormat
          mult.format.protocols/Release
          (release*
            [_]
            (close! op|)
            (close! cmd|))
          #?(:clj clojure.lang.IDeref)
          #?(:clj (deref [_] @stateA))
          #?(:cljs cljs.core/IDeref)
          #?(:cljs (-deref [_] @stateA)))]

    (reset! stateA (merge
                    opts
                    {::opts opts
                     ::mult.editor.spec/editor editor
                     ::mult.format.spec/op| op|
                     ::mult.format.spec/cmd| cmd|}))
    (swap! registryA assoc id)
    (go
      (loop []
        (let [[value port] (alts! [cmd| op|])]
          (when value
            (condp = port

              op|
              (condp = (:op value)

                ::mult.editor.spec/evt-did-change-active-text-editor
                (let [{:keys []} value]
                  (do nil))
                (do ::ignore-other-ops))


              cmd|
              (condp = (:op value)

                ::mult.format.spec/cmd-format-current-form
                (let [text-editor (mult.editor.protocols/active-text-editor* editor)
                      text (mult.editor.protocols/text* text-editor)
                      text-formatted (cljfmt.core/reformat-string text)]
                  (<! (mult.editor.protocols/replace* text-editor text-formatted))
                  (println ::cmd-format-current-form))
                (do ::ignore-other-cmds)))
            (recur)))))
    mult-format))

(defmulti release
  "Releases the instance"
  {:arglists '([id] [instance])} (fn [x & args] (type x)))
(defmethod release :default
  [id]
  (when-let [instance (get @registryA id)]
    (release instance)))
(defmethod release ::mult.format.spec/mult-format
  [instance]
  {:pre [(s/assert ::mult.format.spec/mult-format instance)]}
  (mult.format.protocols/release* instance)
  (swap! registryA dissoc (get @instance ::id)))

(defn text->ns-symbol
  [text-editor filepath]
  (let [range [0 0 100 0]
        text (mult.editor.protocols/text* text-editor range)
        node (p/parse-string text)
        zloc (z/of-string (n/string node))
        ns-symbol (-> zloc z/down z/right z/sexpr)]
    ns-symbol
    #_(when first-line
        (let [ns-string (subs first-line 4)
              ns-symbol (symbol ns-string)]
          ns-symbol))
    #_(prn active-text-editor.document.languageId)))