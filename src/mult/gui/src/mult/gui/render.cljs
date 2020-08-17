(ns mult.gui.render
  (:require
   [clojure.core.async :as a :refer [<! >!  chan go alt! take! put! offer! poll! alts! pub sub
                                     timeout close! to-chan go-loop sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [goog.string :refer [format]]
   [cljs.reader :refer [read-string]]
   [clojure.pprint :refer [pprint]]
   [reagent.core :as r]
   [reagent.dom :as rdom]

   [mult.spec]
   [mult.spec]))

(declare rc-main)

(defn create-state
  [data]
  (r/atom data))

(defn render-ui
  [channels state {:keys [id] :or {id "ui"}}]
  (rdom/render [rc-main channels state]  (.getElementById js/document id)))

(def channels (let [tab| (chan 10)
                    ops| (chan 10)]
                {:tab|  tab|
                 :ops| ops|}))

(defn rc-main
  [{:keys [ops|]} state]
  (r/with-let
    [conf (r/cursor state [::mult.spec/settings])
     data (r/cursor state [::mult.spec/])
     ns-sym (r/cursor state [:ns-sym])
     lrepl-id (r/cursor state [:lrepl-id])]
    (if (empty? @state)

      [:div "loading..."]
      [:<>
       #_[:div {} "rc-repl-tab"]
       #_[:button {:on-click (fn [e]
                               (println "button clicked")
                               #_(put! ops| ???))} "button"]
       #_[:div ":conf"]
       #_[:div {} (with-out-str (pprint @conf))]
       [:div @lrepl-id]
       [:div @ns-sym]
       [:br]
       [:div ":data"]
       [:section
        (map-indexed (fn [i v]
                       ^{:key i} [:pre {} (with-out-str (pprint v))])
                     @data)]])))

