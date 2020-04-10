(ns mult.impl.lrepl
  (:require
   [clojure.core.async :as a :refer [<! >!  chan go alt! take! put! offer! poll! alts! pub sub unsub
                                     timeout close! to-chan  mult tap untap mix admix unmix
                                     pipeline pipeline-async go-loop sliding-buffer dropping-buffer]]
   [goog.string :refer [format]]
   [clojure.string :as string]
   [clojure.pprint :refer [pprint]]
   #_["fs" :as fs]
   #_["path" :as path]
   #_["net" :as net]
   #_["bencode" :as bencode]
   #_["nrepl-client" :as nrepl-client]
   [cljs.reader :refer [read-string]]
   [bencode-cljc.core :refer [serialize deserialize]]
   [mult.protocols :as p]
   [mult.impl.channels :as channels]
   [mult.impl.async :as mult.async]
   [cljs.nodejs :as node]
   ))

(def path (node/require "fs"))
(def fs (node/require "path"))
(def net (node/require "net"))
(def bencode (node/require "bencode"))
(def nrepl-client (node/require "nrepl-client"))


(defn netsocket
  [opts]
  (let [{:keys [topic-fn xf-send xf-msg]
         :or {xf-send identity
              xf-msg identity
              topic-fn (constantly nil)}} opts
        status| (chan (sliding-buffer 10))
        send| (chan (sliding-buffer 1))
        msg| (chan (sliding-buffer 10))
        msg|m (mult msg|)
        msg|p (mult.async/pub (tap msg|m (chan (sliding-buffer 10))) topic-fn (fn [_] (sliding-buffer 10)))
        netsock|i (channels/netsock|i)
        socket (doto (net.Socket.)
                 (.on "connect" (fn [] (put! status| (p/-vl-connected netsock|i opts))))
                 (.on "ready" (fn [] (put! status| (p/-vl-ready netsock|i opts))))
                 (.on "timeout" (fn [] (put! status| (p/-vl-timeout netsock|i  opts))))
                 (.on "close" (fn [hadError] (put! status| (p/-vl-disconnected netsock|i hadError opts))))
                 (.on "error" (fn [err] (put! status| (p/-vl-error netsock|i err opts))))
                 (.on "data" (fn [buf]
                               (try
                                 (when-let [d (xf-msg buf)]
                                   (when (:id d)
                                     (put! msg| (p/-vl-data netsock|i d opts))))
                                 (catch js/Error e #_(println (ex-message e)))))))

        lookup (merge opts {:status| status|
                            :send| send|
                            :msg| msg|
                            :msg|m msg|m
                            :msg|p msg|p})
        conn (reify
               p/Connect
               (-connect [_] (.connect socket (clj->js opts)))
               (-disconnect [_] (.end socket))
               (-connected? [_] (not socket.connecting) #_(not socket.pending))
               p/Send
               (-send [_ v] (try
                              (let [d (xf-send v)]
                                (.write socket d))
                              (catch js/Error e #_(println (ex-message e)))))
               p/Release
               (-release [_] (close! send|))
               cljs.core/ILookup
               (-lookup [_ k] (-lookup _ k nil))
               (-lookup [_ k not-found] (-lookup lookup k not-found)))
        release #(do
                   (p/-disconnect conn)
                   (a/unsub-all msg|p)
                   (a/untap-all msg|m)
                   (close! send|)
                   (close! msg|)
                   (close! status|))]
    (go-loop []
      (when-let [v (<! send|)]
        (p/-send conn v)
        (recur))
      (release))
    conn))

(defn nrepl
  []
  (let [proc| (chan 10)]
    (go-loop []
      (if-let [v (<! proc|)]
        (recur)))
    (reify
      p/Eval
      (-eval [_ code session-id {:keys [msg|p send|]}]
        (go
          (let [id (str (random-uuid))
                topic id
                c| (chan 10)
                res| (chan 50)
                msg|s (sub msg|p topic c|)
                release #(do
                            (close! res|)
                            (close! c|)
                            (unsub msg|p topic c|)
                            (mult.async/close-topic msg|p topic))
                req {:op "eval" :code code :id id}]
            (>! send| req)
            (loop [t| (timeout 10000)]
              (alt!
                c| ([v] (when v
                          (let [{:keys [data opts]} v]
                            (>! res| data)
                            (if (or (:status data) (:err data))
                              (do (release)
                                  {:req  req
                                   :res (<! (a/into [] res|))})
                              (recur t|)))))
                t| (do
                     (release)
                     (ex-info "Nrepl op timed out" [code session-id session-id])))))))
      p/NRepl
      (-close-session [_ session-id opts])
      (-describe [_  opts])
      (-interrupt [_ session-id opts])
      (-ls-sessions [_]))))


(defn lrepl-plain
  []
  (let [nr (nrepl)]
    (reify
      p/Eval
      (-eval [_ code session-id {:keys [msg|p send|] :as opts}]
        (p/-eval nr code session-id opts)))))

(defn lrepl-shadow-clj
  []
  (let [nr (nrepl)]
    (reify
      p/Eval
      (-eval [_ code session-id {:keys [msg|p send|] :as opts}]
        (p/-eval nr code session-id opts)))))

(defn lrepl-shadow-cljs
  [{:keys [build]}]
  (let [nr (nrepl)]
    (reify
      p/Eval
      (-eval [_ code session-id {:keys [msg|p send|] :as opts}]
        (p/-eval nr code session-id opts)))))

(comment

  (as-> {:op :eval :id (str (random-uuid)) :code "(+ 1 2)"} x
    (.encode bencode (clj->js x))
    (.decode bencode x "utf8")
    (js->clj x :keywordize-keys true))


  (do
    (def s (netsocket {:host "localhost" :port 7788
                       :topic-fn #(get-in % [:data :id])
                       :xf-send #(.encode bencode (clj->js %))
                       :xf-msg #(as-> % v
                                  (.toString v)
                                  (.decode bencode v "utf8")
                                  (js->clj v :keywordize-keys true))}))
    (go-loop []
      (when-let [v (<! (:status| s))]
        (println v)
        (recur)))
    #_(go-loop [c| (tap (:msg|m s) (chan 10))]
        (when-let [v (<! c|)]
          (println "value from msg|m")
          (println v)
          (recur c|)))
    (p.conn/-connect s))



  (p.conn/-disconnect s)

  (def nr (nrepl))
  (take! (p.conn/-eval nr "(+ 1 2)" nil (select-keys s [:msg|p :send|])) prn)

  (take! (:status| s) prn)
  (take! (tap (:msg|m s) (chan 1)) prn)

  ;;
  )


(comment

  (do
    (def c (.connect nrepl-client #js {:port 8899
                                       :host "localhost"}))
    (doto c
      (.on "connect" (fn []
                       (println "; net/Socket connect")))
      (.on "ready" (fn []
                     (println "; net/Socket ready")))
      (.on "timeout" (fn []
                       (println "; net/Socket timeout")))
      (.on "close" (fn [hadError]
                     (println "; net/Socket close")
                     (println (format "hadError %s"  hadError))))
      (.on "error" (fn [e]
                     (println "; net/Socket error")
                     (println e)))))

  (.end c)


  (def code "shadow.cljs.devtools.api/compile")
  (.eval c "conj" (fn [err result]
                    (println ".eval data")
                    (println (or err result))))
  (.lsSessions c (fn [err data]
                   (println ".lsSessions data")
                   (println data)))

  (.describe c (fn [err data]
                 (println ".describe data")
                 (println messages)))


  ;;
  )