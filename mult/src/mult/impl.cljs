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
   [mult.protocols :refer [Procs Proc]]))

(defn pret [x]
  (binding [*out* *out* #_*err*]
    (pprint x))
  x)

(defn explain [result comment & data]
  (pprint comment)
  {:result result
   :comment comment
   :data data})


(defn proc*
  [])




(defn proc-impl--
  ([proc-fn channels argm]
   (proc-impl (random-uuid) proc-fn channels argm))
  ([k proc-fn channels argm]
   (let [proc (atom nil)
         proc| (chan 1)
         lookup {:k k
                 :channels channels
                 :argm argm
                 :proc| proc|}]
     (reify
       Proc
       (-start [_]
         (cond
           (some? @proc) (explain false (format "process %s is already running" k))
           :else (let [c| (chan 1)
                       channels (merge channels {:proc| proc|})]
                   (reset! proc (apply proc-fn channels argm))
                   (put! proc| {:op :start :c| c|})
                   c|)))
       (-stop [_]
         (go
           (let [c| (chan 1)]
             (put! proc| {:op :stop :c| c|})
             (let [v (<! c|)]
               (reset! proc nil)
               (close! proc|)
               (close! c|)
               v))))
       (-running? [_] (do
                        (some? @proc)))
       ILookup
       (-lookup [coll k]
         (-lookup coll k nil))
       (-lookup [coll k not-found]
         (-lookup lookup k not-found))))))


(defn procs-impl
  ([opts]
   (procs* opts nil))
  ([opts ctx]
   (let [{procs-map :procs
          up :up
          down :down} opts
         proc| (chan 10)]
     (go (loop [context ctx
                 state {:up? false}
                 procs {}]
           
           
           
           ))
     (reify Procs
       (-start [_ k]
         (go
           (let [c| (chan 1)]
             (>! proc| {:op :start :proc/id k :c| c|})
             (let [v (<! c|)]
               (>! system| {:ch/topic [k :started] :data v})
               v)))
         )
       )
     ))
  )

(defn procs-impl--
  ([opts]
   (procs-impl opts nil))
  ([{procs-map :procs
     up :up
     down :down} ctx]
   (let [context (atom ctx)
         procs (atom {})
         local-state (atom {:up? false})
         map-missing? #(not (contains? procs-map k))
         cotext-missing? #(not @context)
         proc-exists? #(contains? @procs k)
         explain-context-missing #(explain true (format "context is missing"))
         explain-map-missing #(explain true (format "process %s is not in the procs-map" %) %)
         explain-proc-exists #(explain true (format "process %s already exists" %) %)
         explain-proc-not-exists #(explain false (format "process %s does not exist" %) %)
         lookup {:context context
                 :procs procs
                 :procs-map procs-map}]
     (reify
       Procs
       (-start [_ k] (cond
                       (cotext-missing?) (explain-context-missing k)
                       (map-missing? k) (explain-map-missing k)
                       (proc-exists? k) (explain-proc-exists k)
                       :else (let [{:keys [proc-fn channels argm]} (get procs-map k)
                                   system| (get-in @context [:channels :system|])
                                   p (proc-impl k proc-fn (channels (:channels @context)) (argm (:argm @context)))
                                   c| (-start p)]
                               (go
                                 (let [v (<! c|)]
                                   (swap! procs assoc k p)
                                   (put! system| {:ch/topic [k :started] :vl v})
                                   v)))))
       (-stop [_ k] (cond
                      (not (proc-exists? k)) (explain-proc-not-exists k)
                      :else (let [p (get @procs k)
                                  c| (-stop p)]
                              (go
                                (let [v (<! c|)]
                                  (swap! procs dissoc k)
                                  (put! system| {:ch/topic [k :stopped] :vl v})
                                  v)))))
       (-restart [this k] (cond
                            (map-missing? k) (explain-map-missing k)
                            :else (let []
                                    (go
                                      (<! (-stop this k))
                                      (<! (-start this k))))))
       (-up
         ([th]
          (-up th @context))
         ([th ctx]
          (reset! context ctx)
          (go
            (let [v (<! (up th (:channels ctx) (:argm ctx)))]
              (swap! local-state assoc :up? true)
              v))))
       (-down [th]
         (go
           (let [v (<! (down th (:channels @context) (:argm @context)))]
             (swap! local-state assoc :up? false)
             v)))
       (-downup [th]
         (go
           (<! (-down th))
           (<! (-up th @context))))
       (-up? [th]
         (:up? @local-state))
       ILookup
       (-lookup [coll k]
         (-lookup coll k nil))
       (-lookup [coll k not-found]
         (-lookup lookup k not-found))))))
