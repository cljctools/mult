(ns mult.impl.tabapp
  (:require
   [clojure.core.async :as a :refer [<! >!  chan go alt! take! put! offer! poll! alts! pub sub
                                     timeout close! to-chan go-loop sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [goog.string :refer [format]]
   [cljs.reader :refer [read-string]]
   [clojure.pprint :refer [pprint]]
   [reagent.core :as r]
   [reagent.dom :as rdom]
   [mult.protocols :as p]
   [mult.impl.channels :as channels]))

(declare proc-main proc-ops render-ui vscode)

(when (exists? js/acquireVsCodeApi)
  (defonce vscode (js/acquireVsCodeApi)))

(def channels (let [tab| (chan 10)
                    ops| (chan 10)]
                {:tab|  tab|
                 :ops| ops|}))

(def state (r/atom {:data []
                    :conf nil}))

(defn ^:export main
  []
  (proc-main channels {:state state}))

(defn proc-main
  [{:keys [tab| ops|] :as channels} ctx]
  (let []
    (do
      (.addEventListener js/window "message"
                         (fn [ev]
                           (println ev.data)
                           (put! tab| (read-string ev.data))))
      (proc-ops channels ctx)
      (render-ui channels (select-keys ctx [:state])))
    (go (loop []
          (try
            (when-let [[port v] (alts! [ops|])]
              (condp = port
                ops| (.postMessage vscode (pr-str v))))
            (catch js/Error e (do (println "; proc-main error, will resume") (println e))))
          (recur))
        (println "; proc-main go-block exiting, but it shouldn't"))))

(defn proc-ops
  [{:keys [tab| ops|] :as channels} ctx]
  (let [tab|i (channels/tab|i)]
    (go (loop []
          (try
            (when-let [v (<! tab|)]
              (condp = (p/-op tab|i v)
                (p/-op-tab-append tab|i) (let [{:keys [data]} v]
                                           (swap! state update :data conj data))
                (p/-op-conf tab|i) (let [{:keys [conf]} v]
                                     (swap! state assoc :conf conf)))
              (recur))
            (catch js/Error e (do (println "; proc-ops error, will exit") (println e)))))
        (println "proc-ops go-block exiting"))))

(defn rc-repl-tab
  [{:keys [ops|]} ratoms]
  (r/with-let [conf (r/cursor (ratoms :state) [:conf])
               data (r/cursor (ratoms :state) [:data])]
    [:<>
     [:div {} "rc-repl-tab"]
     [:button {:on-click (fn [e]
                           (println "button clicked")
                           #_(put! ops| ???))} "button"]
     [:div ":conf"]
     [:div {} (with-out-str (pprint @conf))]
     [:br]
     [:div ":data"]
     [:div {} (with-out-str (pprint @data))]]))

(defn render-ui
  [channels ratoms]
  (rdom/render [rc-repl-tab channels ratoms]  (.getElementById js/document "ui")))