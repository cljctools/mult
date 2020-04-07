(ns mult.extension
  (:require
   [clojure.core.async :as a :refer [chan go go-loop <! >!  take! put! offer! poll! alt! alts! close!
                                     pub sub unsub mult tap untap mix admix unmix
                                     timeout to-chan  sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [goog.string :refer [format]]
   [clojure.string :as string]
   [cljs.reader :refer [read-string]]
   [clojure.pprint :refer [pprint]]
   
   [mult.protocols.channels :as p.channels]
   [mult.protocols.main| :as p.main|]
   [mult.protocols.editor| :as p.editor|]
   [mult.protocols.ops| :as p.ops|]
   [mult.protocols.tab| :as p.tab|]
   [mult.protocols.tab :as p.tab]
   [mult.protocols.conn :as p.conn]
   [mult.impl.editor :as editor]
   [mult.impl.channels :as channels]))

(def channels (let [main| (chan 10)
                    main|m (mult main|)
                    log| (chan 100)
                    log|m (mult log|)
                    cmd| (chan 100)
                    cmd|m (mult cmd|)
                    ops| (chan 10)
                    ops|m (mult ops|)
                    editor| (chan 10)
                    editor|m (mult editor|)
                    #_editor|p #_(pub (tap editor|m (chan 10)) channels/TOPIC (fn [_] 10))]
                {:main| main|
                 :main|m main|m
                 :log| log|
                 :log|m log|m
                 :cmd| cmd|
                 :cmd|m cmd|m
                 :ops| ops|
                 :ops|m ops|m
                 :editor| editor|
                 :editor|m editor|m}))

(declare proc-main proc-ops proc-log)


; repl only
(def ^:private proc-main-state (atom {}))

(defn proc-main
  [channels ctx]
  (let [pid [:proc-main]
        {:keys [main| main|m log| ops|]} channels
        main|t (tap main|m (chan 10))
        main|i (channels/main|i)
        ops|i (channels/ops|i)
        log|i (channels/log|i)
        log (fn [& args] (put! log| (apply p.channels/-log log|i args)))]
    (go (loop [state {:channels channels
                      :ctx ctx
                      :procs {}
                      :activated? false}]
          (reset! proc-main-state state)
          (try
            (if-let [v (<! main|t)]
              (condp = (p.channels/-op main|i v)
                (p.main|/-op-init main|i) (let [{:keys [channels ctx]} state]
                                            (do
                                              (proc-log channels ctx)
                                              (proc-ops channels ctx)))
                (p.main|/-op-proc-started main|i) (let [{:keys [proc-id proc|]} v]
                                                    #_(log (format "; process started: %s" proc-id))
                                                    (recur (-> state
                                                               (update-in [:procs] assoc proc-id proc|))))
                (p.main|/-op-proc-stopped main|i) (let [{:keys [proc-id]} v]
                                                    #_(log (format "; process stopped: %s" proc-id))
                                                    (recur (-> state
                                                               (update-in [:procs] dissoc proc-id))))
                #_(p.main|/-op-start-proc main|i) #_(let [{:keys [proc-fn]} v
                                                          {:keys [channels ctx]} state]
                                                      (log (format "; process starting: %s" proc-id))
                                                      (proc-fn channels ctx))
                (p.main|/-op-stop-proc main|i) (let [{:keys [proc-id]} v
                                                     chans (-> (get state :procs)
                                                               (keep (fn [[k v]] (when (= (first k) proc-id) v))))]
                                                 (if (empty? chans)
                                                   (log (format "; could not stop process, no such proc-id: %s" proc-id))
                                                   (doseq [c| chans]
                                                     #_(log (format "; process stopping: %s" proc-id))
                                                     (put! c| v))))
                (p.main|/-op-restart-proc main|i) (let [{:keys [proc-id]} v]
                                                    (>! main| (p.main|/-stop-proc main|i proc-id))
                                                    (>! main| (p.main|/-start-proc main|i proc-id)))
                (p.main|/-op-activate main|i) (let [{:keys [editor-context]} v
                                                    state' (update-in state [:ctx] assoc :editor-context  editor-context)]
                                                (when-not (:activated? state)
                                                  (do (editor/proc-editor (:channels state') (:ctx state')))
                                                  (>! ops| (p.ops|/-activate ops|i))
                                                  (recur state')))
                (p.main|/-op-deactivate main|i) (let []
                                                  (when (:activated? state)
                                                    (>! ops| (p.ops|/-deactivate ops|i))
                                                    (>! main| (p.main|/-stop-proc main|i :proc-editor))
                                                    (recur (assoc state :activated? false)))))
              (recur state))
            (catch js/Error e (do (println "; proc-main error, will resume")
                                  (println e))))
          (recur state))
        (log "; proc-main go-block exiting, but it shouldn't"))))

(defn default-commands
  []
  ["mult.open"
   "mult.ping"
   "mult.eval"])

(defn proc-ops
  [channels ctx]
  (let [pid [:proc-ops (random-uuid)]
        {:keys [main| log| editor| ops|m cmd|m]} channels
        proc| (chan 1)
        ops|t (tap ops|m (chan 10))
        cmd|t (tap cmd|m (chan 100))
        editor|i (channels/editor|i)
        main|i (channels/main|i)
        ops|i (channels/ops|i)
        tab|i (channels/tab|i)
        log|i (channels/log|i)
        cmd|i (channels/cmd|i)
        conn|i (channels/conn|i)
        log (fn [& args] (put! log| (apply p.channels/-log log|i args)))
        release! #(do
                    (untap ops|m ops|t)
                    (untap cmd|m cmd|t)
                    (close! ops|t)
                    (close! cmd|t)
                    (close! proc|)
                    (put! main| (p.main|/-proc-stopped main|i pid)))]
    (put! main| (p.main|/-proc-started main|i pid proc|))
    (go (loop [state {:tabs {}
                      :mult.edn nil}]
          (try
            (if-let [[v port] (alts! [cmd|t ops|t proc|])]
              (condp = port
                proc| (release!)
                ops|t (let [op (p.channels/-op ops|i v)]
                        (condp = op
                          (p.ops|/-op-activate ops|i) (do (>! editor| (p.editor|/-register-commands editor|i (default-commands)))
                                                          (>! editor| (p.editor|/-show-info-msg editor|i "actiavting")))
                          (p.ops|/-op-deactivate ops|i) (do (>! editor| (p.editor|/-show-info-msg editor|i "deactiavting")))
                          (p.ops|/-op-tab-created ops|i) (let [{:keys [tab]} v]
                                                           (p.tab/-put! tab (p.tab|/-conf tab|i (get state :mult.edn)))
                                                           (recur (update state :tabs assoc (:id tab) tab)))
                          (p.ops|/-op-tab-disposed ops|i) (let [{:keys [tab/id]} v]
                                                            (do nil)))
                        (recur state))
                cmd|t (let [cmd (:cmd/id v)]
                        (condp = cmd
                          "mult.open" (let [out| (chan 1)
                                            _ (>! editor| (p.editor|/-read-conf editor|i ".vscode/mult.edn" out|))
                                            {:keys [conf]} (<! out|)]
                                        (>! editor| (p.editor|/-show-info-msg editor|i "mult.open"))
                                        (>! editor| (p.editor|/-create-tab editor|i (random-uuid)))
                                        (recur (assoc state :mult.edn conf)))
                          "mult.ping" (do
                                        (>! editor| (p.editor|/-show-info-msg editor|i "mult.ping via channels"))
                                        (<! (timeout 3000))
                                        (>! editor| (p.editor|/-show-info-msg editor|i "mult.ping via channels later")))
                          "mult.eval"  (let [tabs (:tabs state)]
                                         (doseq [tab (vals tabs)]
                                           (p.tab/-put! tab (p.tab|/-append tab|i {:value (rand-int 100)})))))
                        (recur state))))
            (catch js/Error e (do (log "; proc-ops error, will exit" e)))
            (finally
              (release!))))
        (println "; proc-ops go-block exits"))))


; repl only
(def ^:private proc-log-state (atom {}))

(defn proc-log
  [channels ctx]
  (let [pid [:proc-log (random-uuid)]
        {:keys [main| main|m log| log|m  editor|m cmd|m]} channels
        proc| (chan 1)
        main|t (tap main|m (chan 10))
        log|t (tap log|m (chan 10))
        editor|t (tap editor|m (chan 10))
        cmd|t (tap cmd|m (chan 100))
        main|i (channels/main|i)
        append #(-> %1
                    (update-in [:log] conj %2)
                    (update-in [:log] (partial take-last 50)))
        release! #(do
                    (untap main|m main|t)
                    (untap log|m log|t)
                    (untap editor|m  editor|t)
                    (untap cmd|m  cmd|t)
                    (close! main|t)
                    (close! log|t)
                    (close! editor|t)
                    (close! cmd|t)
                    (close! proc|)
                    (put! main| (p.main|/-proc-stopped main|i pid)))]
    (put! main| (p.main|/-proc-started main|i pid proc|))
    (go (loop [state {:log []}]
          (reset! proc-log-state state)
          (try
            (if-let [[v port] (alts! [log|t main|t editor|t cmd|t proc|])]
              (cond
                (= port proc|) (release!)
                :else (let [printable (condp = port
                                        log|t (let [{:keys [id comment data]} v]
                                                {:comment comment
                                                 :data data})
                                        main|t (let []
                                                 v)
                                        editor|t (let []
                                                   v)
                                        cmd|t (let []
                                                v))]
                        (pprint printable)
                        (recur (append state v)))))
            (catch js/Error e (do (println "; proc-log error, will exit")
                                  (println e)))
            (finally
              (release!))))
        (println "; proc-log go-block exits"))))

(defonce _ (let [main|i (channels/main|i)]
             (put! (channels :main|) (p.main|/-init main|i))
             (proc-main channels {})))

(def activate (let [main|i (channels/main|i)]
                (fn [context]
                  (put! (channels :main|) (p.main|/-activate main|i context)))))
(def deactivate (let [main|i (channels/main|i)]
                  (fn []
                    (put! (channels :main|) (p.main|/-deactivate main|i)))))
(def exports #js {:activate activate
                  :deactivate deactivate})

#_(defn reload
    []
    (.log js/console "Reloading...")
    (js-delete js/require.cache (js/require.resolve "./main")))
