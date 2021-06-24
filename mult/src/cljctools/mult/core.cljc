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
   [cljctools.edit.core :as edit.core]

   [cljctools.mult.spec :as mult.spec]
   [cljctools.mult.protocols :as mult.protocols]
   [cljctools.mult.runtime.nrepl :as mult.runtime.nrepl]
   [cljctools.mult.impl :as mult.impl]))

(s/def ::create-opts (s/keys :req [::mult.spec/config
                                   ::mult.spec/editor]
                             :opt []))


(s/def ::send| ::mult.spec/channel)
(s/def ::recv| ::mult.spec/channel)
(s/def ::recv|mult ::mult.spec/mult)

(s/def ::tabs (s/coll-of ::mult.spec/tab :into #{}))

(s/def ::nrepl-connections (s/map-of ::mult.spec/nrepl-id ::mult.spec/nrepl-connection))

(defn create
  [{:keys [::mult.spec/config
           ::mult.spec/editor] :as opts}]
  {:pre [(s/assert ::create-opts opts)]
   :post [(s/assert ::mult.spec/cljctools-mult %)]}
  (let [stateA (atom nil)
        tab-recv| (chan 10)
        tab-evt| (chan 10)
        ops| (chan 10)
        cmd| (chan 10)

        create-tab
        (fn create-tab
          []
          (let [tab-id (str (random-uuid))
                tab (mult.protocols/create-tab*
                     editor
                     {::mult.spec/tab-id tab-id
                      ::mult.spec/tab-title "mult"
                      ::mult.spec/on-tab-closed (fn [tab]
                                                  (put! tab-evt| {:op ::mult.spec/on-tab-closed
                                                                  ::mult.spec/tab-id tab-id}))
                      ::mult.spec/on-tab-message (fn [tab msg]
                                                   (put! tab-recv| (read-string msg)))})]
            (mult.protocols/open* tab)
            (mult.impl/send-data tab {:op ::mult.spec/op-update-ui-state
                                      ::mult.spec/config config})
            (swap! stateA update ::tabs assoc tab-id tab)))

        release-tab
        (fn release-tab
          [tab-id tab]
          (mult.protocols/release* tab)
          (swap! stateA update ::tabs dissoc tab-id))

        nrepl-connections
        (persistent!
         (reduce (fn [result {:keys [::mult.spec/nrepl-id] :as nrepl-meta}]
                   (assoc! result nrepl-id
                           (mult.runtime.nrepl/create-nrepl-connection
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
              (mult.protocols/release* tab)
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
                     ::mult.spec/editor editor
                     ::nrepl-connections nrepl-connections
                     ::tabs {}
                     ::mult.spec/ops| ops|
                     ::mult.spec/cmd| cmd|}))
    (doseq [_ (range 0 (::mult.spec/open-n-tabs-on-start config))]
      (create-tab))

    (go
      (loop []
        (let [[value port] (alts! [tab-evt| cmd| ops|])]
          (when value
            (condp = port

              ops|
              (condp = (:op value)

                ::mult.spec/evt-did-change-active-text-editor
                (let [{:keys []} value
                      active-text-editor (mult.protocols/active-text-editor* editor)
                      filepath (mult.protocols/filepath* active-text-editor)]
                  (when filepath
                    (let [text (mult.protocols/text* active-text-editor [0 0 100 0])
                          ns-symbol (edit.core/read-ns-symbol text)
                          nrepl-ids (mult.impl/filepath-to-nrepl-ids
                                     config
                                     filepath)
                          nrepl-id (first nrepl-ids)]
                      (when ns-symbol
                        (doseq [[tab-id tab] (get @stateA ::tabs)]
                          (when (mult.protocols/visible?* tab)
                            (mult.impl/send-data tab {:op ::mult.spec/op-update-ui-state
                                                      ::mult.spec/ns-symbol ns-symbol
                                                      ::mult.spec/nrepl-id  nrepl-id})))))))

                (do ::ignore-other-ops))

              tab-evt|
              (condp = (:op value)

                ::mult.spec/on-tab-closed
                (let [{:keys [::mult.spec/tab-id]} value]
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
                  (mult.protocols/show-notification* editor (str ::cmd-ping))
                  #_(mult.protocols/send* tab (pr-str {:op ::mult.spec/op-ping})))

                ::mult.spec/cmd-eval
                (let [active-text-editor (mult.protocols/active-text-editor* editor)
                      filepath (mult.protocols/filepath* active-text-editor)]
                  (when filepath
                    (let [text (mult.protocols/text* active-text-editor [0 0 100 0])
                          ns-symbol (edit.core/read-ns-symbol text)
                          code-string (mult.protocols/selection* active-text-editor)
                          nrepl-ids (mult.impl/filepath-to-nrepl-ids
                                     config
                                     filepath)
                          nrepl-id (first nrepl-ids)
                          nrepl-connection (get-in @stateA [::nrepl-connections nrepl-id])]
                      (when (and ns-symbol nrepl-connection)
                        (let [{:keys [::mult.spec/runtime]} @nrepl-connection
                              #_code-string-formatted
                              #_(cond
                                  (= runtime :clj)
                                  (format "(do (in-ns '%s) %s)" ns-symbol code-string)

                                  (= runtime :cljs)
                                  (format "(binding [*ns* (find-ns '%s)] %s)" ns-symbol code-string))
                              {:keys [value err out]} (<! (mult.protocols/eval*
                                                           nrepl-connection
                                                           {::mult.spec/code-string code-string
                                                            ::mult.spec/ns-symbol ns-symbol}))]
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
                              (when (mult.protocols/visible?* tab)
                                (mult.impl/send-data tab {:op ::mult.spec/op-update-ui-state
                                                          ::mult.spec/eval-value value
                                                          ::mult.spec/eval-out out
                                                          ::mult.spec/eval-err err})))))))))

                (do ::ignore-other-cmds)))
            (recur)))))
    cljctools-mult))