(ns cljctools.mult.editor.tabapp.impl
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
   [cljctools.mult.editor.protocols :as editor.protocols]
   [cljctools.mult.editor.spec :as editor.spec]))

(declare vscode)

(when (exists? js/acquireVsCodeApi)
  (defonce vscode (js/acquireVsCodeApi)))

(s/def ::create-opts (s/and
                      ::editor.spec/create-tabapp-opts
                      (s/keys :req []
                              :opt [])))
(defn create
  [{:as opts
    :keys [::editor.spec/id
           ::editor.spec/on-message]}]
  {:pre [(s/assert ::create-opts opts)]}
  (let [stateA (atom nil)

        tabapp
        ^{:type ::editor.spec/tabapp}
        (reify
          editor.protocols/TabApp
          editor.protocols/Send
          (send*
            [_ msg]
            (.postMessage vscode msg))

          editor.protocols/Release
          (release*
            [_]
            (do nil))

          cljs.core/IDeref
          (-deref [_] @stateA))]
    (when on-message
      (.addEventListener js/window "message"
                         (fn [ev]
                           (on-message ev.data)
                           #_(put! recv| (read-string ev.data)))))
    (reset! stateA (merge
                    opts
                    {::opts opts}))
    tabapp))