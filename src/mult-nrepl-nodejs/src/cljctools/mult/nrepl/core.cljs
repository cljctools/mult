(ns cljctools.mult.nrepl.core
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
   [clojure.spec.alpha :as s]

   [cljctools.mult.nrepl.protocols :as mult.nrepl.protocols]
   [cljctools.mult.nrepl.spec :as mult.nrepl.spec]))

(def fs (js/require "fs"))
(def path (js/require "path"))
(def NreplClient (js/require "nrepl-client"))

(s/def ::nrepl-client (s/nilable some?))

(defn create-nrepl-connection
  [{:as opts
    :keys [::mult.nrepl.spec/host
           ::mult.nrepl.spec/port
           ::mult.nrepl.spec/nrepl-type
           ::mult.nrepl.spec/shadow-build-key
           ::mult.nrepl.spec/runtime]}]
  {:pre [(s/assert ::mult.nrepl.spec/create-nrepl-connection-opts opts)]
   :post [(s/assert ::mult.nrepl.spec/nrepl-connection %)]}
  (let [stateA (atom nil)

        reponses->map
        (fn reponses->map
          [responses]
          (let [data (->>
                      (reduce
                       (fn [result response]
                         (if (:value response)
                           (assoc result :value (clojure.string/join "" [(:value result) (:value response)]))
                           (merge result response)))
                       {}
                       (js->clj responses :keywordize-keys true)))]
            data))

        connected?
        (fn connected?
          []
          (and (get @stateA ::nrepl-client)
               (= (.. (get @stateA ::nrepl-client) -readyState) "open")))

        eval-fn
        (fn eval-fn
          [{:as opts
            :keys [::mult.nrepl.spec/session-id
                   ::mult.nrepl.spec/ns-symbol
                   ::mult.nrepl.spec/code-string]}]
          (let [nrepl-client (get @stateA ::nrepl-client)
                result| (chan 1)]
            (let [session-id (or session-id (get @stateA ::mult.nrepl.spec/session-id))]
              (.eval nrepl-client code-string (str ns-symbol) session-id
                     (fn [err responses]
                       (println :eval-err (js->clj err :keywordize-keys true))
                       (println :eval-responses (js->clj responses :keywordize-keys true))
                       (if err
                         (do (println ::err err)
                             (put! result| err))
                         (put! result| (reponses->map responses))))))
            result|))

        clone-fn
        (fn clone-fn
          [{:as opts
            :keys [::mult.nrepl.spec/session-id]}]
          (let [nrepl-client (get @stateA ::nrepl-client)
                result| (chan 1)]
            (let [session-id (or session-id (get @stateA ::mult.nrepl.spec/session-id))]
              (.clone nrepl-client session-id
                      (fn [err responses]
                        (if err
                          (do (println ::err err)
                              (put! result| err))
                          (do
                            (put! result| (reponses->map responses)))))))
            result|))


        init-session-fn
        (fn init-session-fn
          [nrepl-connection]
          (go
            (let [{:keys [:new-session]} (<! (clone-fn {}))]
              (swap! stateA assoc ::mult.nrepl.spec/session-id new-session)
              (println :new-session new-session)
              new-session)))

        init-fns {[:nrepl :clj]
                  (fn init-fn
                    [nrepl-connection]
                    (go
                      true))

                  [:shadow-cljs :cljs]
                  (fn init-fn
                    [nrepl-connection]
                    (go
                      (let [{:keys [::mult.nrepl.spec/session-id
                                    ::mult.nrepl.spec/shadow-build-key]} @nrepl-connection
                            code-string (format
                                         "(shadow.cljs.devtools.api/nrepl-select %s)"
                                         shadow-build-key)]
                        (<! (eval-fn
                             {::mult.nrepl.spec/code-string code-string
                              ::mult.nrepl.spec/session-id session-id}))
                        (println :init-fn-done)
                        true)))

                  [:shadow-cljs :clj]
                  (fn init-fn
                    [nrepl-connection]
                    (go
                      true))}

        initialize
        (fn initialize
          [nrepl-connection]
          (if (and (connected?)
                   (get @stateA ::mult.nrepl.spec/session-id))
            (go true)
            (go
              (let [{:keys [::mult.nrepl.spec/nrepl-type
                            ::mult.nrepl.spec/runtime]} @stateA
                    init-fn (get init-fns [nrepl-type runtime])]
                (<! (init-session-fn nrepl-connection))
                (<!  (init-fn nrepl-connection))
                true))))



        nrepl-connection
        ^{:type ::mult.nrepl.spec/nrepl-connection}
        (reify
          mult.nrepl.protocols/NreplConnection
          (eval*
            [this {:as opts
                   :keys [::mult.nrepl.spec/session-id
                          ::mult.nrepl.spec/code-string]}]
            {:pre [(s/assert ::mult.nrepl.spec/eval-opts opts)]}
            (go
                ;; lazy connect on eval, making it http request/response like
                ;; if user wants to eval and app is down, the response will be 'not connected'
                ;; once the app is up, evals will work
                ;; so no need for reconnecting socket, the request-lazy connect-response is better
              (<! (mult.nrepl.protocols/connect* this))
              (<! (initialize this))
              (<! (eval-fn opts))))

          (clone*
            [this {:as opts
                   :keys [::mult.nrepl.spec/session-id]}]
            {:pre [(s/assert ::mult.nrepl.spec/clone-opts opts)]}
            (go
              (<! (mult.nrepl.protocols/connect* this))
              (<! (clone-fn opts))))

          (connect*
            [this]
            (if (connected?)
              (go true)
              (let [result| (chan 1)]
                (let [nrepl-client (.connect NreplClient
                                             (->
                                              (get @stateA ::opts)
                                              (select-keys [::mult.nrepl.spec/host
                                                            ::mult.nrepl.spec/port])
                                              (clj->js)))]
                  (doto nrepl-client
                    (.on "ready" (fn []
                                   (put! result| true)))
                    (.on "close" (fn [code reason]
                                   (mult.nrepl.protocols/disconnect* this))))
                  (swap! stateA assoc ::nrepl-client nrepl-client))
                result|)))

          (connect*
            [this {:keys [::mult.nrepl.spec/host
                          ::mult.nrepl.spec/port] :as opts}]
            {:pre [(s/assert ::mult.nrepl.spec/connect-opts opts)]}
            (swap! stateA merge opts {::opts opts})
            (mult.nrepl.protocols/connect* this))

          (disconnect*
            [this]
            (when-let [nrepl-client  (get @stateA ::nrepl-client)]
              (.end nrepl-client)
              (swap! stateA dissoc ::nrepl-client ::mult.nrepl.spec/session-id)))

          mult.nrepl.protocols/Release
          (release*
            [this]
            (mult.nrepl.protocols/disconnect* this))

          cljs.core/IDeref
          (-deref [_] @stateA))]
    (reset! stateA (merge
                    opts
                    {::opts opts
                     ::mult.nrepl.spec/session-id nil
                     ::nrepl-client nil}))
    nrepl-connection))