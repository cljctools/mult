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

   [cljctools.mult.spec :as mult.spec]
   [cljctools.mult.core :as mult.core]))

(do (clojure.spec.alpha/check-asserts true))

(s/def ::foo  (s/or :keyword keyword? :string string?))

(def exports #js {:activate (fn [context]
                              (println ::activate)
                              (mult.core/create {::mult.core/id ::mult}))
                  :deactivate (fn []
                                (println ::deactivate)
                                (mult.core/release {::mult.core/id ::mult}))})
(when (exists? js/module)
  (set! js/module.exports exports))

(defn ^:export main [] (println ::main))