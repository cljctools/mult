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
    :keys [::mult.nrepl.spec/nrepl-id
           ::mult.nrepl.spec/host
           ::mult.nrepl.spec/port
           ::mult.nrepl.spec/nrepl-type
           ::mult.nrepl.spec/shadow-build-key
           ::mult.nrepl.spec/runtime]}]
  {:pre [(s/assert ::mult.nrepl.spec/nrepl-meta opts)]
   :post [(s/assert ::mult.nrepl.spec/nrepl-connection %)]}
  (let [stateA (atom nil)

        init-session-fn
        (fn init-session-fn
          [nrepl-connection]
          (go
            (let [value-string (<! (mult.nrepl.protocols/clone*
                                    nrepl-connection {}))
                  {:keys [:new-session] :as value} (read-string value-string)]
              (println ::value-string value-string)
              (println ::value value)
              (swap! stateA assoc ::mult.nrepl.spec/session-id new-session))
            new-session))

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
                        (<! (mult.nrepl.protocols/eval*
                             nrepl-connection
                             {::mult.nrepl.spec/code-string code-string
                              ::mult.nrepl.spec/session-id session-id})))
                      true))

                  [:shadow-cljs :clj]
                  (fn init-fn
                    [nrepl-connection]
                    (go
                      true))}

        reponses->value-string
        (fn reponses->string
          [responses]
          (let [value-string (->>
                              (persistent!
                               (reduce
                                (fn [result response]
                                  (if (:value response)
                                    (conj! result (:value response))
                                    result))
                                (transient [])
                                (js->clj responses :keywordize-keys true)))
                              (clojure.string/join ""))]
            value-string))

        nrepl-connection
        ^{:type ::mult.nrepl.spec/nrepl-connection}
        (reify
          mult.nrepl.protocols/NreplConnection
          (eval*
            [this {:as opts
                   :keys [::mult.nrepl.spec/session-id
                          ::mult.nrepl.spec/code-string]}]
            {:pre [(s/assert ::mult.nrepl.spec/eval-opts opts)]}
            (let [nrepl-client (get @stateA ::nrepl-client)
                  result| (chan 1)]
              (go
                ;; lazy connect on eval, making it http request/response like
                ;; if user wants to eval and app down, the response will be 'not connected'
                ;; once the app is up, evals will work
                ;; so no need for reconnecting socket, the request-lazy connect-response is better
                (<! (mult.nrepl.protocols/connect* this))
                (.eval nrepl-client code-string session-id
                       (fn [err responses]
                         (if err
                           (do (println ::err err)
                               (put! result| err))
                           (put! result| (reponses->value-string responses))))))
              result|))

          (clone*
            [this {:as opts
                   :keys [::mult.nrepl.spec/session-id]}]
            {:pre [(s/assert ::mult.nrepl.spec/clone-opts opts)]}
            (let [nrepl-client (get @stateA ::nrepl-client)
                  result| (chan 1)]
              (.clone nrepl-client session-id
                      (fn [err responses]
                        (if err
                          (do (println ::err err)
                              (put! result| err))
                          (put! result| (reponses->value-string responses)))))
              result|))

          (connect*
            [this]
            (if (and (get @stateA ::nrepl-client)
                     (= (.. nrepl-client -readyState) "open"))
              (go true))
            (go
              (let [nrepl-client (.connect NreplClient
                                           (->
                                            (get @stateA ::opts)
                                            (select-keys [::mult.nrepl.spec/host
                                                          ::mult.nrepl.spec/port])
                                            (clj->js)))
                    {:keys [::mult.nrepl.spec/nrepl-type
                            ::mult.nrepl.spec/runtime]} @stateA
                    init-fn (get init-fns [nrepl-type runtime])]
                (doto nrepl-client
                  (.on "close" (fn [code reason]
                                 (mult.nrepl.protocols/disconnect* this))))
                (swap! stateA assoc ::nrepl-client nrepl-client)
                (<! (init-session-fn this))
                (<!  (init-fn this)))))
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