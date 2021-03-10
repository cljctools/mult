(ns cljctools.mult.extension.main
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

   [cljctools.mult.spec :as mult.spec]
   [cljctools.mult.editor :as mult.editor]
   [cljctools.nrepl-client]
   [cljctools.socket]
   [cljctools.socket.nodejs-net]))

(defonce ^:private registryA (atom {}))
(defonce ^:private registry-connectionsA (atom {}))
(defonce ^:private registry-tabsA (atom {}))

(declare )

(defn mount
  [{:keys [::id
           ::mult.editor/context] :as opts}]
  (go
    (let [tab-recv| (chan 10)
          tab-evt| (chan 10)
          cmd| (chan 10)
          tab {::mult.editor/tab-id "mult-tab"
               ::mult.editor/context context
               ::mult.editor/tab-title "mult"
               ::mult.editor/tab-recv| tab-recv|
               ::mult.editor/tab-evt| tab-evt|}
          procsA (atom [])
          stop-procs (fn []
                       (doseq [[stop| proc|] @procsA]
                         (close! stop|))
                       (a/merge (mapv second @procsA)))

          stateA (atom (merge
                        opts
                        {::opts opts
                         ::tab tab
                         ::stop-procs stop-procs}))]

      (swap! registryA assoc id stateA)
      (mult.editor/register-commands {::mult.editor/cmd-ids
                                      #{"mult.open"
                                        "mult.ping"
                                        "mult.eval"}
                                      ::mult.editor/context context
                                      ::mult.editor/cmd| cmd|})
      (mult.editor/open-tab tab)

      (let [stop| (chan 1)
            proc|
            (go
              (loop []
                (let [[value port] (alts! [stop| cmd|])]
                  (condp = port

                    stop|
                    (do nil)

                    tab-evt|
                    (when-let [{:keys [:op ::mult.editor/tab-id]} value]
                      (condp = op

                        ::mult.editor/onDidDispose
                        (let []
                          (println ::tab-disposed tab-id)))
                      (recur))

                    cmd|
                    (when-let [{:keys [::mult.editor/cmd-id]} value]

                      (condp = cmd-id

                        "mult.open"
                        (let []
                          (println "mult.open")
                          (<! (mult.editor/open-tab tab)))

                        "mult.ping"
                        (let []
                          (mult.editor/show-information-message "mult.ping")))
                      (recur))))))]
        (swap! procsA conj [stop| proc|])))))

(defn unmount
  [{:keys [::id] :as opts}]
  (go
    (let []
      (let [state @(get @registryA id)]
        (when (::stop-procs state)
          (<! (mult.editor/close-tab (::tab state)))
          (<! ((::stop-procs state))))
        (swap! registryA dissoc id)))))

(def exports #js {:activate (fn [context]
                              (println ::activate)
                              (mount {::id :main
                                      ::mult.editor/context context}))
                  :deactivate (fn []
                                (println ::deactivate)
                                (unmount {::id :main}))})
(when (exists? js/module)
  (set! js/module.exports exports))

(defn ^:export main [& args]
  (println ::main))