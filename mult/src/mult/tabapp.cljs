(ns mult.tabapp
  (:require
   [clojure.core.async :as a :refer [<! >!  chan go alt! take! put! offer! poll! alts! pub sub
                                     timeout close! to-chan go-loop sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [goog.string :refer [format]]
   [cljs.reader :refer [read-string]]
   [reagent.core :as r]
   [reagent.dom :as rdom]))

(declare proc-main proc-render render-ui proc-ops acquireVsCodeApi)

(def vscode  (js/acquireVsCodeApi))

(def channels (let [system| (chan 10)
                    system|pub (pub system| :ch/topic (fn [_] 10))
                    inputs| (chan (sliding-buffer 10))]
                {:system|  system|
                 :system|pub system|pub
                 :inputs| inputs|}))

(def ratoms {:state (r/atom {:counter 0})})

(defn ^:export main
  []
  (put! (channels :system|) {:ch/topic :proc-main :proc/op :start})
  (proc-main channels ratoms))

#_(go
    (<! (timeout 1))
    (main))

(defn proc-main
  [{:keys [system| system|pub inputs|] :as channels} ratoms]
  (let [sys| (chan 1)]
    (sub system|pub :proc-main sys|)
    (go (loop []
          (when-let [{op :proc/op} (<! sys|)]
            (println (format "proc-main %s" op))
            (condp = op
              :start (do
                       (.addEventListener js/window "message"
                                          (fn [ev]
                                            (put! inputs| (read-string ev.data))))
                       (proc-render (select-keys channels [:system|pub :inputs|]) ratoms)
                       (proc-ops (select-keys channels [:system|pub :inputs|]) ratoms)
                       (put! system| {:ch/topic :proc-render :proc/op :render})
                       (recur)))))
        (println "closing  proc-main"))))

(defn proc-ops
  [{:keys [system| system|pub inputs|] :as channels} ratoms]
  (let [sys| (chan 1)]
    (sub system|pub :proc-ops sys|)
    (go (loop []
          (when-let [[v port] (alts! [inputs|])]
            (condp = port
              inputs| (let [{:keys [op]} v]
                        (println (format "proc-ops %s" op))
                        (condp = op
                          :tabapp/inc (do
                                        (swap! (ratoms :state) update :counter inc))
                          :tabapp/pong (let []
                                         (do
                                           (.postMessage
                                            vscode
                                            (str {:op :tabapp/pong
                                                  :counter (get-in @(ratoms :state) [:counter])})))))

                        (recur)))))
        (println "closing proc-ops"))))

(defn proc-render
  [{:keys [system|pub inputs|]} ratoms]
  (let [sys| (chan 1)]
    (sub system|pub :proc-render sys|)
    (go (loop []
          (let [{:keys [proc/op]} (<! sys|)]
            (println (format "proc-render %s" op))
            (condp = op
              :render (do
                        (render-ui (select-keys channels [:inputs|]) ratoms)
                        (recur)))))
        (println "closing proc-render"))))


(defn rc-repl-tab
  [{:keys [inputs|]} ratoms]
  (r/with-let [counter (r/cursor (ratoms :state) [:counter] )]
    [:<>
     [:div {} "rc-repl-tab"]
     [:button {:on-click (fn [e]
                           (put! inputs| {:op :tabapp/pong}))} "pong"]
     [:div {} (format ":counter %s" @counter)]]))

(defn render-ui
  [channels ratoms]
  (rdom/render [rc-repl-tab channels ratoms]  (.getElementById js/document "ui")))