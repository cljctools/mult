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

   
   [cljctools.socket.spec :as socket.spec]
   [cljctools.socket.core :as socket.core]

   [cljctools.mult.spec :as mult.spec]
   [cljctools.mult.protocols :as mult.protocols]

   [cljctools.mult.logical-repl :as mult.logical-repl]))

(do (clojure.spec.alpha/check-asserts true))

(s/def ::id  (s/or :keyword keyword? :string string?))

(s/def ::create-opts (s/keys :req [::id
                                   ::mult.spec/config
                                   ::mult.spec/editor
                                   ::mult.spec/cmd|
                                   ::socket.spec/create-opts-net-socket]
                             :opt [::socket.spec/create-opts-websocket]))

(s/def ::send| ::mult.spec/channel)
(s/def ::recv| ::mult.spec/channel)
(s/def ::recv|mult ::mult.spec/mult)

(defonce ^:private registryA (atom {}))

(declare active-ns
         send-data
         filepath->logical-repl-meta-ids)

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

        connections (persistent!
                     (reduce (fn [result {:keys [::mult.spec/connection-meta-id
                                                 ::mult.spec/connection-opts
                                                 ::mult.spec/connection-opts-type] :as connection-meta}]
                               (assoc! result connection-meta-id
                                       (merge
                                        connection-meta
                                        (condp = connection-opts-type

                                          ::socket.spec/tcp-socket-opts
                                          (let [socket (socket.core/open
                                                        (merge
                                                         (create-opts-net-socket connection-opts)
                                                         {::socket.spec/connect? true
                                                          ::socket.spec/reconnection-timeout 2000}))]
                                            {::socket.spec/socket socket
                                             ::send| (get @socket ::socket.spec/send|)
                                             ::recv| (get @socket ::socket.spec/recv|)
                                             ::recv|mult (get @socket ::socket.spec/recv|mult)})
                                          (do (println ::connection-opts-type-not-supported))))))
                             (transient {})
                             (get config ::mult.spec/connection-metas)))

        logical-repls (persistent!
                       (reduce (fn [result {:keys [::mult.spec/logical-repl-meta-id] :as logical-repl-meta}]
                                 (assoc! result logical-repl-meta-id
                                         (mult.logical-repl/create logical-repl-meta)))
                               (transient {})
                               (get config ::mult.spec/logical-repl-metas)))

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
          #?(:clj clojure.lang.IDeref)
          #?(:clj (deref [_] @stateA))
          #?(:cljs cljs.core/IDeref)
          #?(:cljs (-deref [_] @stateA)))]
    
    (doseq [[logical-repl-meta-id logical-repl] logical-repls
            :let [{:keys [::mult.spec/connection-meta-id
                          ::mult.logical-repl/recv|
                          ::mult.logical-repl/send|]} @logical-repl
                  connection (get connections  connection-meta-id)]
            :when connection]
      #_(println ::tapping logical-repl-meta-id connection-meta-id)
      (tap (::recv|mult connection) recv|)
      (pipe send| (::send| connection) false))

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
                      {:keys [::mult.spec/filepath
                              ::mult.spec/ns-symbol]} (active-ns editor)
                      selection-string (mult.protocols/selection* active-text-editor)
                      logical-repl-meta-ids (filepath->logical-repl-meta-ids
                                             config
                                             filepath)
                      first-logical-repl (get logical-repls (first logical-repl-meta-ids))
                      {:keys [value]} (<! (mult.protocols/eval*
                                           first-logical-repl
                                           {::mult.spec/code-string selection-string
                                            ::mult.spec/ns-symbol ns-symbol}))]
                  (send-data tab {:op ::mult.spec/op-eval
                                  ::mult.spec/eval-result value}))))
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

#_(defn parse-ns
    "Safely tries to read the first form from the source text.
   Returns ns name or nil.
   But: does not work if there are reader conditionals"
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
    (let [filepath (mult.protocols/filepath* text-editor)
          range [0 0 1 0]
          first-line (mult.protocols/text* text-editor range)
          ns-string (subs first-line 4)
          ns-symbol (symbol ns-string)
          data {::mult.spec/filepath filepath
                ::mult.spec/ns-symbol ns-symbol}]
      data
      #_(prn active-text-editor.document.languageId))))

(defn filepath->logical-repl-meta-ids
  [config filepath]
  (into []
        (comp
         (filter (fn [{:keys [::mult.spec/logical-repl-meta-id
                              ::mult.spec/include-file?]}]
                   (include-file? filepath)))
         (map ::mult.spec/logical-repl-meta-id))
        (::mult.spec/logical-repl-metas config)))