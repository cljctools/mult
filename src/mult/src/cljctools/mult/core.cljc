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

   [cljctools.mult.editor.spec :as mult.editor.spec]
   [cljctools.mult.editor.protocols :as mult.editor.protocols]

   [cljctools.mult.nrepl.protocols :as mult.nrepl.protocols]
   [cljctools.mult.nrepl.spec :as mult.nrepl.spec]
   [cljctools.mult.nrepl.core :as mult.nrepl.core]

   [cljctools.mult.spec :as mult.spec]
   [cljctools.mult.protocols :as mult.protocols]

   [cljctools.edit.core :as edit.core]))

(do (clojure.spec.alpha/check-asserts true))

(s/def ::id  (s/or :keyword keyword? :string string?))


(s/def ::create-opts (s/keys :req [::id
                                   ::mult.spec/config
                                   ::mult.editor.spec/editor]
                             :opt []))


(s/def ::send| ::mult.spec/channel)
(s/def ::recv| ::mult.spec/channel)
(s/def ::recv|mult ::mult.spec/mult)

(s/def ::tabs (s/coll-of ::mult.editor.spec/tab :into #{}))

(s/def ::nrepl-connections (s/map-of ::mult.spec/nrepl-id ::mult.nrepl.spec/nrepl-connection))

(defonce ^:private registryA (atom {}))

(declare read-ns-symbol
         send-data
         filepath->nrepl-ids)

(defn create
  [{:keys [::id
           ::mult.spec/config
           ::mult.editor.spec/editor] :as opts}]
  {:pre [(s/assert ::create-opts opts)]
   :post [(s/assert ::mult.spec/cljctools-mult %)]}
  (let [stateA (atom nil)
        tab-recv| (chan 10)
        tab-evt| (chan 10)
        op| (chan 10)
        cmd| (chan 10)

        create-tab
        (fn create-tab
          []
          (let [tab-id (str (random-uuid))
                tab (mult.editor.protocols/create-tab*
                     editor
                     {::mult.editor.spec/tab-id tab-id
                      ::mult.editor.spec/tab-title "mult"
                      ::mult.editor.spec/on-tab-closed (fn [tab]
                                                         (put! tab-evt| {:op ::mult.editor.spec/on-tab-closed
                                                                         ::mult.editor.spec/tab-id tab-id}))
                      ::mult.editor.spec/on-tab-message (fn [tab msg]
                                                          (put! tab-recv| (read-string msg)))})]
            (mult.editor.protocols/open* tab)
            (send-data tab {:op ::mult.spec/op-update-ui-state
                            ::mult.spec/config config})
            (swap! stateA update ::tabs assoc tab-id tab)))

        release-tab
        (fn release-tab
          [tab-id tab]
          (mult.editor.protocols/release* tab)
          (swap! stateA update ::tabs dissoc tab-id))

        nrepl-connections
        (persistent!
         (reduce (fn [result {:keys [::mult.spec/nrepl-id] :as nrepl-meta}]
                   (assoc! result nrepl-id
                           (mult.nrepl.core/create-nrepl-connection
                            nrepl-meta)))
                 (transient {})
                 (get config ::mult.spec/nrepl-metas)))

        cljctools-mult
        ^{:type ::mult.spec/cljctools-mult}
        (reify
          mult.protocols/CljctoolsMult
          mult.protocols/Release
          (release*
            [_]
            (doseq [[tab-id tab] (get @stateA ::tabs)]
              (mult.editor.protocols/release* tab)
              (swap! stateA update ::tabs dissoc tab-id))
            (close! tab-evt|)
            (close! tab-recv|))
          #?(:clj clojure.lang.IDeref)
          #?(:clj (deref [_] @stateA))
          #?(:cljs cljs.core/IDeref)
          #?(:cljs (-deref [_] @stateA)))]

    (reset! stateA (merge
                    opts
                    {::opts opts
                     ::mult.editor.spec/editor editor
                     ::nrepl-connections nrepl-connections
                     ::tabs {}
                     ::mult.spec/op| op|
                     ::mult.spec/cmd| cmd|}))
    (swap! registryA assoc id)
    (doseq [_ (range 0 (::mult.spec/open-n-tabs-on-start config))]
      (create-tab))

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
                    (let [text (mult.editor.protocols/text* active-text-editor [0 0 100 0])
                          ns-symbol (edit.core/read-ns-symbol text)
                          nrepl-ids (filepath->nrepl-ids
                                     config
                                     filepath)
                          nrepl-id (first nrepl-ids)]
                      (when ns-symbol
                        (doseq [[tab-id tab] (get @stateA ::tabs)]
                          (when (mult.editor.protocols/visible?* tab)
                            (send-data tab {:op ::mult.spec/op-update-ui-state
                                            ::mult.spec/ns-symbol ns-symbol
                                            ::mult.spec/nrepl-id  nrepl-id})))))))

                ::mult.spec/op-select-logical-tab
                (let [{:keys []} value]
                  (println ::op-select-logical-tab))
                (do ::ignore-other-ops))

              tab-evt|
              (condp = (:op value)

                ::mult.editor.spec/on-tab-closed
                (let [{:keys [::mult.editor.spec/tab-id]} value]
                  (swap! stateA update ::tabs dissoc tab-id)
                  (println ::tab-disposed)))

              cmd|
              (condp = (:op value)

                ::mult.spec/cmd-open
                (let []
                  (create-tab))

                ::mult.spec/cmd-ping
                (let []
                  (println ::cmd-ping)
                  (mult.editor.protocols/show-notification* editor (str ::cmd-ping))
                  #_(mult.editor.protocols/send* tab (pr-str {:op ::mult.spec/op-ping})))

                ::mult.spec/cmd-eval
                (let [active-text-editor (mult.editor.protocols/active-text-editor* editor)
                      filepath (mult.editor.protocols/filepath* active-text-editor)]
                  (when filepath
                    (let [text (mult.editor.protocols/text* active-text-editor [0 0 100 0])
                          ns-symbol (edit.core/read-ns-symbol text)
                          code-string (mult.editor.protocols/selection* active-text-editor)
                          nrepl-ids (filepath->nrepl-ids
                                     config
                                     filepath)
                          nrepl-id (first nrepl-ids)
                          nrepl-connection (get-in @stateA [::nrepl-connections nrepl-id])]
                      (when (and ns-symbol nrepl-connection)
                        (let [{:keys [::mult.nrepl.spec/runtime]} @nrepl-connection
                              #_code-string-formatted
                              #_(cond
                                  (= runtime :clj)
                                  (format "(do (in-ns '%s) %s)" ns-symbol code-string)

                                  (= runtime :cljs)
                                  (format "(binding [*ns* (find-ns '%s)] %s)" ns-symbol code-string))
                              {:keys [value err out]} (<! (mult.nrepl.protocols/eval*
                                                           nrepl-connection
                                                           {::mult.nrepl.spec/code-string code-string
                                                            ::mult.nrepl.spec/ns-symbol ns-symbol}))]

                          (if (zero? (count (get @stateA ::tabs)))
                            (do
                              ;; if no tabs, we print eval results directly to console
                              (println (format "%s => \n\n %s \n\n" ns-symbol code-string))
                              (when out
                                (println out))
                              (when err
                                (println err))
                              (println value))
                            (doseq [[tab-id tab] (get @stateA ::tabs)]
                              (when (mult.editor.protocols/visible?* tab)
                                (send-data tab {:op ::mult.spec/op-update-ui-state
                                                ::mult.spec/eval-value value
                                                ::mult.spec/eval-out out
                                                ::mult.spec/eval-err err})))))))))

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

(defn filepath->nrepl-ids
  [config filepath]
  (let [opts {:namespaces {'foo.bar {'x 1}}}
        sci-ctx (sci/init opts)]
    (into []
          (comp
           (filter (fn [{:keys [::mult.spec/nrepl-id
                                ::mult.spec/include-file?]}]
                     (let [include-file?-fn (sci/eval-string* sci-ctx (pr-str include-file?))]
                       (include-file?-fn filepath))))
           (map ::mult.spec/nrepl-id))
          (::mult.spec/nrepl-metas config))))