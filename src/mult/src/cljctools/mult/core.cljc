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

   [sci.core :as sci]

   [cljctools.socket.spec :as socket.spec]
   [cljctools.socket.core :as socket.core]

   [cljctools.mult.editor.spec :as mult.editor.spec]
   [cljctools.mult.editor.protocols :as mult.editor.protocols]

   [cljctools.mult.fmt.spec :as mult.fmt.spec]
   [cljctools.mult.fmt.protocols :as mult.fmt.protocols]
   [cljctools.mult.fmt.core :as mult.fmt.core]

   [cljctools.mult.spec :as mult.spec]
   [cljctools.mult.protocols :as mult.protocols]

   [cljctools.mult.logical-repl :as mult.logical-repl]))

(do (clojure.spec.alpha/check-asserts true))

(s/def ::id  (s/or :keyword keyword? :string string?))


(s/def ::create-opts (s/keys :req [::id
                                   ::mult.spec/config
                                   ::mult.editor.spec/editor
                                   ::socket.spec/create-opts-net-socket]
                             :opt [::socket.spec/create-opts-websocket]))


(s/def ::send| ::mult.spec/channel)
(s/def ::recv| ::mult.spec/channel)
(s/def ::recv|mult ::mult.spec/mult)

(defonce ^:private registryA (atom {}))

(declare read-ns-symbol
         send-data
         filepath->logical-repl-ids)

(defonce ui-stateA (atom
                    ^{:type ::mult.spec/ui-state}
                    {}))

(defn create
  [{:keys [::id
           ::socket.spec/create-opts-net-socket
           ::socket.spec/create-opts-websocket
           ::mult.spec/config
           ::mult.editor.spec/editor] :as opts}]
  {:pre [(s/assert ::create-opts opts)]
   :post [(s/assert ::mult.spec/cljctools-mult %)]}
  (let [stateA (atom nil)
        tab-recv| (chan 10)
        tab-evt| (chan 10)
        op| (chan 10)
        cmd| (chan 10)

        tab (mult.editor.protocols/create-tab*
             editor
             {::mult.editor.spec/tab-id "mult-tab"
              ::mult.editor.spec/tab-title "mult"
              ::mult.editor.spec/on-tab-closed (fn [tab]
                                                 (put! tab-evt| {:op ::mult.editor.spec/on-tab-closed}))
              ::mult.editor.spec/on-tab-message (fn [tab msg]
                                                  (put! tab-recv| (read-string msg)))})

        connections (persistent!
                     (reduce (fn [result {:keys [::mult.spec/connection-id
                                                 ::mult.spec/connection-opts
                                                 ::mult.spec/connection-opts-type] :as connection-meta}]
                               (assoc! result connection-id
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
                       (reduce (fn [result {:keys [::mult.spec/logical-repl-id] :as logical-repl-meta}]
                                 (assoc! result logical-repl-id
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
            (mult.editor.protocols/release* tab)
            (close! tab-evt|)
            (close! tab-recv|))
          #?(:clj clojure.lang.IDeref)
          #?(:clj (deref [_] @stateA))
          #?(:cljs cljs.core/IDeref)
          #?(:cljs (-deref [_] @stateA)))]

    (doseq [[logical-repl-id logical-repl] logical-repls
            :let [{:keys [::mult.spec/connection-id
                          ::mult.logical-repl/recv|
                          ::mult.logical-repl/send|]} @logical-repl
                  connection (get connections  connection-id)]
            :when connection]
      #_(println ::tapping logical-repl-id connection-id)
      (tap (::recv|mult connection) recv|)
      (pipe send| (::send| connection) false))

    (reset! stateA (merge
                    opts
                    {::opts opts
                     ::mult.editor.spec/editor editor
                     ::mult.editor.spec/tab tab
                     ::mult.spec/op| op|
                     ::mult.spec/cmd| cmd|}))
    (swap! registryA assoc id)
    (add-watch ui-stateA ::ui-state
               (fn [k refA old-state new-state]
                 (send-data tab {:op ::mult.spec/op-update-ui-state
                                 ::mult.spec/ui-state new-state})))
    (do
      (mult.editor.protocols/open* tab)
      (swap! ui-stateA assoc ::mult.spec/config config))
    (go
      (loop []
        (let [[value port] (alts! [tab-evt| cmd| op|])]
          (when value
            (condp = port

              op|
              (condp = (:op value)

                ::mult.editor.spec/evt-did-change-active-text-editor
                (let [{:keys []} value
                      active-text-editor (mult.editor.protocols/active-text-editor* editor)
                      filepath (mult.editor.protocols/filepath* active-text-editor)]
                  (when filepath
                    (let [ns-symbol (mult.fmt.core/text->ns-symbol active-text-editor filepath)
                          logical-repl-ids (filepath->logical-repl-ids
                                            config
                                            filepath)
                          logical-repl (get logical-repls (first logical-repl-ids))]
                      (when (and ns-symbol logical-repl)
                        (println ::evt-did-change-active-text-editor)
                        (swap! ui-stateA merge {::mult.fmt.spec/ns-symbol ns-symbol
                                                ::mult.spec/logical-repl-id (::mult.spec/logical-repl-id @logical-repl)})
                        #_(<! (mult.protocols/on-activate* logical-repl ns-symbol))))))

                ::mult.spec/op-select-logical-tab
                (let [{:keys []} value]
                  (println ::op-select-logical-tab))
                (do ::ignore-other-ops))

              tab-evt|
              (condp = (:op value)

                ::mult.editor.spec/on-tab-closed
                (let [{:keys []} value]
                  (println ::tab-disposed)))

              cmd|
              (condp = (:op value)

                ::mult.spec/cmd-open
                (let []
                  (println ::cmd-open)
                  (mult.editor.protocols/open* tab))

                ::mult.spec/cmd-ping
                (let []
                  (println ::cmd-ping)
                  (mult.editor.protocols/show-notification* editor (str ::cmd-ping))
                  (mult.editor.protocols/send* tab (pr-str {:op ::mult.spec/op-ping})))

                ::mult.spec/cmd-eval
                (let [active-text-editor (mult.editor.protocols/active-text-editor* editor)
                      filepath (mult.editor.protocols/filepath* active-text-editor)]
                  (when filepath
                    (let [ns-symbol (mult.fmt.core/text->ns-symbol active-text-editor filepath)
                          selection-string (mult.editor.protocols/selection* active-text-editor)
                          logical-repl-ids (filepath->logical-repl-ids
                                            config
                                            filepath)
                          logical-repl (get logical-repls (first logical-repl-ids))]
                      (when (and ns-symbol logical-repl)
                        (let [{:keys [value]} (<! (mult.protocols/eval*
                                                   logical-repl
                                                   {::mult.spec/code-string selection-string
                                                    ::mult.fmt.spec/ns-symbol ns-symbol}))]
                          (swap! ui-stateA assoc ::mult.spec/eval-result value))))))

                (do ::ignore-other-cmds)))
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
  {:pre [(s/assert ::mult.spec/op (:op data))]}
  (mult.editor.protocols/send* tab (pr-str data)))

(defn filepath->logical-repl-ids
  [config filepath]
  (let [opts {:namespaces {'foo.bar {'x 1}}}
        sci-ctx (sci/init opts)]
    (into []
          (comp
           (filter (fn [{:keys [::mult.spec/logical-repl-id
                                ::mult.spec/include-file?]}]
                     (let [include-file?-fn (sci/eval-string* sci-ctx (pr-str include-file?))]
                       (include-file?-fn filepath))))
           (map ::mult.spec/logical-repl-id))
          (::mult.spec/logical-repl-metas config))))