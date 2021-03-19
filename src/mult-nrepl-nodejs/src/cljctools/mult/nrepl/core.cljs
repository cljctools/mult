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
           ::mult.nrepl.spec/shadow-build-key
           ::mult.nrepl.spec/runtime]}]
  {:pre [(s/assert ::mult.nrepl.spec/nrepl-meta opts)]
   :post [(s/assert ::mult.nrepl.spec/nrepl-connection %)]}
  (let [stateA (atom nil)

        reponses->value-string
        (fn reponses->string
          [responses]
          (let [value (->>
                       (persistent!
                        (reduce
                         (fn [result response]
                           (if (:value response)
                             (conj! result (:value response))
                             result))
                         (transient [])
                         (js->clj responses :keywordize-keys true)))
                       (clojure.string/join ""))]
            value))

        nrepl-connection
        ^{:type ::mult.nrepl.spec/nrepl-connection}
        (reify
          mult.nrepl.protocols/NreplConnection
          (eval*
            [_ {:as opts
                :keys [::mult.nrepl.spec/session-id
                       ::mult.nrepl.spec/code-string
                       ::mult.nrepl.spec/ns-symbol]}]
            {:pre [(s/assert ::mult.nrepl.spec/eval-opts opts)]}
            (let [nrepl-client (get @stateA ::nrepl-client)
                  result| (chan 1)]
              (.eval nrepl-client code-string (str ns-symbol) session-id
                     (fn [err responses]
                       (if err
                         (do (println ::err err)
                             (put! result| err))
                         (put! result| (reponses->value-string responses)))))

              result|))

          (clone*
            [_ {:as opts
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
            [_]
            (when (get @stateA ::nrepl-client)
              (mult.nrepl.protocols/disconnect* _))
            (let [nrepl-client (.connect NreplClient
                                         (->
                                          (get @stateA ::opts)
                                          (select-keys [::mult.nrepl.spec/host
                                                        ::mult.nrepl.spec/port])
                                          (clj->js)))]
              (swap! stateA assoc ::nrepl-client nrepl-client)))
          (connect*
            [_ {:keys [::mult.nrepl.spec/host
                       ::mult.nrepl.spec/port] :as opts}]
            {:pre [(s/assert ::mult.nrepl.spec/connect-opts opts)]}
            (swap! stateA update ::opts merge opts)
            (mult.nrepl.protocols/connect* _))

          (disconnect*
            [_]
            (when-let [nrepl-client  (get @stateA ::nrepl-client)]
              (.end nrepl-client)
              (swap! stateA dissoc ::nrepl-client)))

          mult.nrepl.protocols/Release
          (release*
            [_]
            (mult.nrepl.protocols/disconnect* _))

          cljs.core/IDeref
          (-deref [_] @stateA))]
    (reset! stateA (merge
                    opts
                    {::opts opts
                     ::nrepl-client nil}))
    nrepl-connection))