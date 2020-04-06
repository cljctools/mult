(ns mult.impl
  (:require
   [clojure.core.async :as a :refer [<! >!  chan go alt! take! put! offer! poll! alts! pub sub
                                     timeout close! to-chan go-loop sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [goog.string :refer [format]]
   [clojure.string :as string]
   [clojure.pprint :refer [pprint]]
   ["fs" :as fs]
   ["path" :as path]
   ["net" :as net]
   ["bencode" :as bencode]
   [cljs.reader :refer [read-string]]
   [bencode-cljc.core :refer [serialize deserialize]]
   [mult.protocols :refer [Procs Proc System| Ops| Procs| Common| PLog Log|]]))

(defn pret [x]
  (binding [*out* *out* #_*err*]
    (pprint x))
  x)

(defn explain [result comment & data]
  (pprint comment)
  {:result result
   :comment comment
   :data data})


(defn system|-interface
  []
  (let []
    (reify System|
      (-proc-started [_ proc-id data]
        {:ch/topic [proc-id :started] :data data})
      (-proc-stopped [_ proc-id data]
        {:ch/topic [proc-id :stopped] :data data})
      (-procs-up [_]
        {:ch/topic :procs/up})
      (-procs-down [_]
        {:ch/topic :procs/down}))))

(defn ops|-interface
  []
  (let []
    (reify Ops|
      (-op-tab-add [_] :tab/add)
      (-op-tab-on-dispose [_] :tab/on-dispose)
      (-tab-add [_ v])
      (-tab-on-dispose [_ id]
        {:op :tab/on-dispose :tab/id id})
      (-tab-send [_ v]
        {:op :tab/send
         :tab/id :current
         :tab/msg {:op :tabapp/inc}}))))

(defn log|-interface
  []
  (let []
    (reify Log|
      (-op-step [_] :log/step)
      (-op-info [_] :log/info)
      (-op-warning [_] :log/warning)
      (-op-error [_] :log/error)
      (-step [_ id step-key comment data]
        {:op (-op-step _) :step/k step-key :log/comment comment :log/data data})
      (-info [_ id comment data]
        {:op (-op-info _) :log/comment comment :log/data data})
      (-warning [_ id comment data]
        {:op (-op-warning _) :log/comment comment :log/data data})
      (-error [_ id comment data]
        {:op (-error _) :log/comment comment :log/data data})
      (-explain [_ id result comment data]
        (pprint comment)
        {:id id
         :result result
         :comment comment
         :data data}))))

(defn proc|-interface
  []
  (reify
    Proc|
    (-op-start [_] :proc/start)
    (-op-stop [_] :proc/stop)
    (-start [_ out|]
      {:op (-op-start _) :out| out|})
    (-stop [_ out|]
      {:op (-op-stop _) :out| out|})))

(defn proc-interface
  [{:keys [proc|]} lookup]
  (let [proc|i (proc|-interface)]
    (reify
      Proc
      (-start
        ([_]
         (-start _ (chan 1)))
        ([_ out|]
         (put! proc| (-start proc|i out|))
         out|))
      (-stop
        ([_]
         (-stop _ (chan 1)))
        ([_ out|]
         (put! proc| (-stop proc|i out|))
         out|))
      (-running? [_]
        (get @lookup :proc))
      ILookup
      (-lookup [coll k]
        (-lookup coll k nil))
      (-lookup [coll k not-found]
        (-lookup @lookup k not-found)))))

(defn proc-impl
  ([proc-fn ctx]
   (proc-impl (random-uuid) proc-fn ctx))
  ([id proc-fn ctx]
   (let [proc| (chan 10)
         {:keys [channels]} ctx
         proc-fn| (chan 1)
         lookup (atom {:id id
                       :proc| proc|
                       :proc-fn| proc-fn|
                       :ctx ctx})
         proc|i (proc|-interface)
         log|i (log|-interface)]
     (go (loop [state {:proc nil}]
           (swap! lookup merge state)
           (if-let [[v port] (alts! [])]
             (condp = port
               proc| (let [{:keys [op]} v
                           proc-exists? #(some? (get state :proc))
                           explain-proc-exists #(-explain log|i [id op] false (format "process %s already exists" id) nil)
                           explain-proc-not-exists #(-explain log|i [id op] false (format "process %s does not exist" id) nil)]
                       (condp = op
                         (-op-start proc|i) (let [{:keys [out|]} v]
                                              (when-let [warning (cond
                                                                   (proc-exists?) (explain-proc-exists))]
                                                (>! out| warning)
                                                (recur state))
                                              (let [p (apply proc-fn (assoc channels :proc| proc-fn|))
                                                    o (<! proc-fn|)]
                                                (>! out| o)
                                                (recur (update state assoc :proc p))))
                         (-op-stop proc|i) (let [{:keys [out|]} v]
                                             (when-let [warning (cond
                                                                  (not (proc-exists?)) (explain-proc-not-exists))]
                                               (>! out| warning)
                                               (recur state))
                                             (let [c| (chan 1)
                                                   o (do (>! proc-fn| (-stop proc|i c|))
                                                         c|)]
                                               (>! out| o)
                                               (close! proc-fn|)
                                               (close! proc|)
                                               (do nil)))))))))
     (proc-interface {:proc| proc|} lookup))))

(defn procs|-interface
  []
  (let []
    (reify Procs|
      (-op-start [_] :proc/start)
      (-op-stop [_] :proc/stop)
      (-op-started [_] :proc/started)
      (-op-stopped [_] :proc/stopped)
      (-op-error [_] :proc/error)
      (-op-restart [_] :proc/restart)
      (-op-up [_] :procs/up)
      (-op-down [_] :procs/down)
      (-op-downup [_] :procs/downup)
      (-start [_ proc-id out|] {:op (-op-start _) :proc/id proc-id :out| out|})
      (-stop [_ proc-id out|] {:op (-op-stop _) :proc/id proc-id :out| out|})
      (-started [_ proc-id] {:op (-op-started _) :proc/id proc-id})
      (-stopped [_ proc-id] {:op (-op-stopped _) :proc/id proc-id})
      (-error [_ proc-id] {:op (-op-error _) :proc/id proc-id})
      (-restart [_ proc-id out|] {:op (-op-restart _) :proc/id proc-id :out| out|})
      (-up [_ ctx out|] {:op (-op-up _) :ctx ctx :out| out|})
      (-down [_  out|] {:op (-op-down _) :out| out|})
      (-downup [_  ctx out|] {:op (-op-downup _) :ctx ctx :out| out|}))))

(defn procs-interface
  [{:keys [proc| system|]} lookup]
  (let [system|i (system|-interface)
        procs|i (procs|-interface)]
    (reify
      Procs
      (-start [_ proc-id]
        (let [c| (chan 1)]
          (put! proc| (-start procs|i proc-id c|))
          c|))
      (-stop [_ proc-id]
        (let [c| (chan 1)]
          (put! proc| (-stop procs|i proc-id c|))
          c|))
      (-restart [_ proc-id]
        (let [c| (chan 1)]
          (put! proc| (-restart procs|i proc-id c|))
          c|))
      (-up [_ ctx]
        (let [c| (chan 1)]
          (put! proc| (-up procs|i ctx c|))
          c|))
      (-down [_]
        (let [c| (chan 1)]
          (put! proc| (-down procs|i  c|))
          c|))
      (-downup [th ctx]
        (let [c| (chan 1)]
          (go
            (<! (-down th))
            (>! c| (<! (-up th ctx))))
          c|))
      (-up? [_]
        (:up? @lookup))
      ILookup
      (-lookup [coll k]
        (-lookup coll k nil))
      (-lookup [coll k not-found]
        (-lookup @lookup k not-found)))))

(defn procs-impl
  ([opts]
   (procs-impl opts nil))
  ([opts ctx]
   (let [{procs-map :procs
          up :up
          down :down} opts
         procs| (chan 10)
         procs|m (mult procs|)
         procs|t (tap procs|m (chan 10))
         procs|i (procs|-interface)
         log|i (log|-interface)
         lookup (atom {:opts opts
                       :procs| procs|
                       :procs|m procs|m
                       :up? nil
                       :procs nil
                       :ctx ctx})]
     (go (loop [state {:ctx ctx
                       :up? false
                       :procs {}}]
           (swap! lookup merge state)
           (if-let [[v port] (alts! [procs|t])]
             (condp = port
               procs|t (let [{:keys [op]} v
                             procs (:procs state)
                             map-missing? #(not (contains? procs-map k))
                             cotext-missing? #(not (get state :ctx))
                             proc-exists? #(contains? (get state :procs) k)
                             explain-context-missing #(-explain log|i false (format "ctx is missing"))
                             explain-map-missing #(-explain log|i false (format "process %s is not in the procs-map" %) %)
                             explain-proc-exists #(-explain log|i false (format "process %s already exists" %) %)
                             explain-proc-not-exists #(-explain log|i false (format "process %s does not exist" %) %)]
                         (condp = op
                           (-op-start procs|i) (let [{:keys [proc/id out|]} v]
                                                 (when-let [warning (cond
                                                                      (cotext-missing?) (explain-context-missing id)
                                                                      (map-missing? id) (explain-map-missing id)
                                                                      (proc-exists? id) (explain-proc-exists id))]
                                                   (>! out| warning)
                                                   (recur state))
                                                 (let [{:keys [proc-fn ctx-fn]} (get procs-map id)
                                                       system| (get-in state [:ctx :channels :system|])
                                                       p (proc-impl id proc-fn (ctx-fn ctx))]
                                                   (take! (-start p) (fn [o]
                                                                       (put! out| o)
                                                                       (put! procs| (-started procs|i id))))
                                                   (recur (update-in state [:procs] assoc k {:proc p :status :starting}))))
                           (-op-started procs|i) (let [{:keys [proc/id]} v]
                                                   (offer! system| (-proc-started system|i id v))
                                                   (recur (update-in state [:procs id] assoc :status :started)))
                           (-op-stop procs|i) (let [{:keys [proc/id out|]} v]
                                                (when-let [warning (cond
                                                                     (not (proc-exists? k)) (explain-proc-not-exists id))]
                                                  (>! out| warning)
                                                  (recur state))
                                                (let [p (get @procs k)]
                                                  (take! (-stop p) (fn [o]
                                                                     (put! out| o)
                                                                     (put! procs| (-stopped procs|i id))))
                                                  (recur (update-in state [:procs k] assoc :status :stopping))))
                           (-op-stopped procs|i) (let [{:keys [proc/id]} v]
                                                   (offer! system| (-proc-stopped system|i k v))
                                                   (recur (update-in state [:procs] dissoc  id)))
                           (-op-error procs|i) (let [{:keys [proc/id]} v]
                                                 (offer! system| (-proc-stopped system|i k v))
                                                 (recur (update-in state [:procs] dissoc id)))
                           (-op-restart procs|i) (let [{:keys [proc/id out|]} v]
                                                   (when-let [warning (cond
                                                                        (map-missing? id) (explain-map-missing id))]
                                                     (>! out| warning)
                                                     (recur state))
                                                   (let [c| (chan 1)]
                                                     (put! procs| (-stop procs|i id c|))
                                                     (take! c| (fn [v]
                                                                 (put! procs| (-start procs|i id out|))))))
                           (-op-up procs|i) (let [{:keys [proc/id out| ctx]} v
                                                  o (<! (up th (:channels ctx) (:ctx ctx) th))]
                                              (>! out| o)
                                              (>! system| (-procs-up system|i k v))
                                              (recur (update state merge {:up? true
                                                                          :context ctx})))
                           (-op-down procs|i) (let [{:keys [out|]}
                                                    o (<! (down th (:channels ctx) (:ctx ctx) th))]
                                                (>! system| (-procs-down system|i k v))
                                                (>! out| o)
                                                (recur (update state merge {:up? false})))
                           (-op-downup procs|i) (let [{:keys [out|]}]
                                                  (let [c| (chan 1)]
                                                    (put! procs| (-down procs|i c|))
                                                    (take! c| (fn [v]
                                                                (put! procs| (-up procs|i out|)))))))
                         (recur state))))))
     (procs-interface channels lookup))))


; repl only
(def ^:private logs (atom {}))

(defn proc-log-impl
  [{:keys [proc| log|m]} ctx]
  (let [log|t (chan 100)]
    (tap log|m log|t)
    (go (loop [state {:log []}]
          (reset! logs state)
          (if-let [[v port] (alts! [log|t])]
            (condp = port
              log|t (let []
                      (recur (update-in state [:log] conj v)))))))))