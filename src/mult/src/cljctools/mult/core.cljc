(ns cljctools.mult.core
  (:require
   [clojure.core.async :as a :refer [chan go go-loop <! >! take! put! offer! poll! alt! alts! close!
                                     pub sub unsub mult tap untap mix admix unmix pipe
                                     timeout to-chan  sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [clojure.string]
   [clojure.pprint :refer [pprint]]
   #?(:cljs [cljs.reader :refer [read-string]])
   #?(:cljs [goog.string.format])
   #?(:cljs [goog.string :refer [format]])
   [clojure.spec.alpha :as s]

   [cljctools.nrepl-client.core :as nrepl-client.core]
   [cljctools.socket.spec :as socket.spec]
   [cljctools.socket.core :as socket.core]

   [cljctools.mult.spec :as mult.spec]
   [cljctools.mult.protocols :as mult.protocols]))

(do (clojure.spec.alpha/check-asserts true))

(s/def ::id  (s/or :keyword keyword? :string string?))

(s/def ::create-opts (s/keys :req [::id
                                   ::mult.spec/config
                                   ::mult.spec/editor
                                   ::mult.spec/cmd|
                                   ::socket.spec/create-opts-net-socket]
                             :opt [::socket.spec/create-opts-websocket]))

(defonce ^:private registryA (atom {}))

(def ^:const NS_DECLARATION_LINE_RANGE 100)

(declare parse-ns
         active-ns
         send-data)

(defn create
  [{:keys [::id
           ::mult.spec/cmd|
           ::socket.spec/create-opts-net-socket
           ::socket.spec/create-opts-websocket
           ::mult.spec/config
           ::mult.spec/editor] :as opts}]
  {:pre [(s/assert ::create-opts opts)]
   :post [(s/assert ::mult.spec/cljctools-mult %)]}
  (let [stateA (atom nil)
        tab-recv| (chan 10)
        tab-evt| (chan 10)

        tab (mult.protocols/create-tab*
             editor
             {::mult.spec/tab-id "mult-tab"
              ::mult.spec/tab-title "mult"
              ::mult.spec/on-tab-closed (fn [tab]
                                          (put! tab-evt| {:op ::tab-closed
                                                          ::mult.spec/tab tab}))
              ::mult.spec/on-tab-message (fn [tab msg]
                                           (put! tab-recv| (read-string msg)))})

        cljctools-mult
        ^{:type ::mult.spec/cljctools-mult}
        (reify
          mult.protocols/CljctoolsMult
          mult.protocols/Release
          (release*
            [_]
            (mult.protocols/release* tab)
            (close! tab-evt|)
            (close! tab-recv|))
          cljs.core/IDeref
          (-deref [_] @stateA))]
    (reset! stateA (merge
                    opts
                    {::opts opts
                     ::mult.spec/editor editor
                     ::mult.spec/tab tab}))
    (swap! registryA assoc id)
    (mult.protocols/open* tab)
    (go
      (loop []
        (let [[value port] (alts! [tab-evt| cmd|])]
          (when value
            (condp = port

              tab-evt|
              (condp = (:op value)

                ::tab-closed
                (let [{:keys [::mult.spec/tab]} value]
                  (println ::tab-disposed)))

              cmd|
              (condp = (:op value)

                ::mult.spec/cmd-open
                (let []
                  (println ::cmd-open)
                  (mult.protocols/open* tab))

                ::mult.spec/cmd-ping
                (let []
                  (println ::cmd-ping)
                  (mult.protocols/show-notification* editor (str ::cmd-ping))
                  (mult.protocols/send* tab (pr-str {:op ::mult.spec/op-ping})))

                ::mult.spec/cmd-eval
                (let [active-text-editor (mult.protocols/active-text-editor* editor)
                      selection (mult.protocols/selection* active-text-editor)]
                  (send-data tab {:op ::mult.spec/op-eval
                                  ::mult.spec/eval-data selection}))))
            (recur)))))
    cljctools-mult))

(defmulti release
  "Releases cljctools-mult instance"
  {:arglists '([id] [cljctools-mult])} (fn [x & args] (type x)))
(defmethod release :default
  [id]
  (when-let [cljctools-mult (get @registryA id)]
    (release cljctools-mult)))
(defmethod release ::cljctools-mult
  [cljctools-mult]
  {:pre [(s/assert ::cljctools-mult cljctools-mult)]}
  (mult.protocols/release* cljctools-mult)
  (swap! registryA dissoc (get @cljctools-mult ::id)))

(defn send-data
  [tab data]
  {:pre [(s/assert ::mult.spec/op-value data)]}
  (mult.protocols/send* tab (pr-str data)))

(defn parse-ns
  "Safely tries to read the first form from the source text.
   Returns ns name or nil"
  [filepath text]
  (try
    (when (re-matches #".+\.clj(s|c)?" filepath)
      (let [fform (read-string text)]
        (when (= (first fform) 'ns)
          (second fform))))
    (catch js/Error error (do
                            (println ::parse-ns filepath)
                            (println error)))))
(defn active-ns
  [editor]
  (when-let [text-editor (mult.protocols/active-text-editor* editor)]
    (let [range [[0 0] [NS_DECLARATION_LINE_RANGE 0]]
          text (mult.protocols/text* text-editor range)
          filepath (mult.protocols/filepath* text-editor)
          ns-symbol (parse-ns filepath text)
          data {::mult.spec/filepath filepath
                ::mult.spec/ns-symbol ns-symbol}]
      data
      #_(prn active-text-editor.document.languageId))))