(ns cljctools.mult.ui.main
  (:require
   [clojure.core.async :as a :refer [chan go go-loop <! >!  take! put! offer! poll! alt! alts! close!
                                     pub sub unsub mult tap untap mix admix unmix pipe
                                     timeout to-chan  sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [cljs.core.async.impl.protocols :refer [closed?]]
   [cljs.core.async.interop :refer-macros [<p!]]
   [goog.string.format :as format]
   [goog.string :refer [format]]
   [goog.object]
   [clojure.string :as str]
   [cljs.reader :refer [read-string]]
   [clojure.spec.alpha :as s]

   [cljctools.mult.ui.core :as mult.ui.core]))

(do (clojure.spec.alpha/check-asserts true))

(declare vscode)

(when (exists? js/acquireVsCodeApi)
  (defonce vscode (js/acquireVsCodeApi)))

(defn ^:export main
  []
  (println ::main)
  (let [recv| (chan 10)
        send| (chan 10)]
    (mult.ui.core/create
     {::mult.ui.core/recv| recv|
      ::mult.ui.core/send| send|})
    (.addEventListener js/window "message"
                       (fn [ev]
                         (put! recv| (read-string ev.data))))
    (go
      (loop []
        (when-let [msg (<! send|)]
          (.postMessage vscode msg)
          (recur))))))

(defn ^:export reload
  []
  (println ::reload))


(do (main))