(ns cljctools.mult.extension.main
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


   [cljctools.nrepl-client]
   [cljctools.socket.spec]
   [cljctools.socket.api]
   [cljctools.socket.nodejs-net]

   [cljctools.mult.spec :as mult.spec]
   [cljctools.mult.editor.api :as editor.api]
   [cljctools.mult.editor.spec :as editor.spec]
   [cljctools.mult.editor.protocols :as editor.protocols]
   [cljctools.mult.editor.impl :as editor.impl]))

(do (clojure.spec.alpha/check-asserts true))

(s/def ::id  (s/or :keyword keyword? :string string?))

(defprotocol Release
  (release* [_]))

(defprotocol Extension
  #_Release
  #_IDeref)

(s/def ::extension #(and
                     (satisfies? Extension %)
                     (satisfies? Release %)
                     (satisfies? cljs.core/IDeref %)))

(s/def ::create-opts (s/keys :req [::id
                                   ::editor.impl/context]
                             :opt []))

(defonce ^:private registryA (atom {}))
(defonce ^:private registry-connectionsA (atom {}))
(defonce ^:private registry-tabsA (atom {}))

(declare)

(defn create
  [{:keys [::id
           ::editor.impl/context] :as opts}]
  {:pre [(s/assert ::create-opts opts)]
   :post [(s/assert ::extension %)]}
  (let [stateA (atom nil)
        tab-recv| (chan 10)
        tab-evt| (chan 10)
        cmd| (chan 10)

        editor (editor.api/create
                {::editor.spec/id ::editor
                 ::editor.impl/context context})

        tab (editor.api/create-tab
             editor
             {::editor.spec/tab-id "mult-tab"
              ::editor.spec/tab-title "mult"
              ::editor.spec/on-tab-closed (fn [tab]
                                            (put! tab-evt| {:op ::tab-closed
                                                            ::editor.spec/tab tab}))
              ::editor.spec/on-tab-message (fn [tab msg]
                                             (put! tab-recv| (read-string msg)))})

        extension
        ^{:type ::extension}
        (reify
          Extension
          Release
          (release*
            [_]
            (editor.protocols/release* tab)
            (editor.protocols/release* editor)
            (close! cmd|)
            (close! tab-evt|)
            (close! tab-recv|))
          cljs.core/IDeref
          (-deref [_] @stateA))]
    (reset! stateA (merge
                    opts
                    {::opts opts
                     ::editor.spec/editor editor
                     ::editor.spec/tab tab}))
    (swap! registryA assoc id)
    (editor.api/register-commands
     editor
     {::editor.spec/cmd-ids
      #{"mult.open"
        "mult.ping"
        "mult.eval"}
      ::editor.spec/cmd| cmd|})
    (editor.protocols/open* tab)
    (go
      (loop []
        (let [[value port] (alts! [tab-evt| cmd|])]
          (when value
            (condp = port

              tab-evt|
              (condp = (:op value)

                ::tab-closed
                (let [{:keys [::editor.spec/tab]} value]
                  (println ::tab-disposed)))

              cmd|
              (condp = (::editor.spec/cmd-id value)

                "mult.open"
                (let []
                  (println "mult.open")
                  (editor.protocols/open* tab))

                "mult.ping"
                (let []
                  (editor.protocols/show-notification* editor "mult.ping")
                  (editor.protocols/send* tab (pr-str {:op ::mult.spec/ping})))))
            (recur)))))
    extension))

(defmulti release
  "Releases extension instance"
  {:arglists '([id] [extension])} (fn [x & args] (type x)))
(defmethod release :default
  [id]
  (when-let [extension (get @registryA id)]
    (release extension)))
(defmethod release ::extension
  [extension]
  {:pre [(s/assert ::extension extension)]}
  (release* extension)
  (swap! registryA dissoc (get @extension ::id)))

(def exports #js {:activate (fn [context]
                              (println ::activate)
                              (create {::id ::main
                                       ::editor.impl/context context}))
                  :deactivate (fn []
                                (println ::deactivate)
                                (release ::main))})
(when (exists? js/module)
  (set! js/module.exports exports))

(defn ^:export main [] (println ::main))