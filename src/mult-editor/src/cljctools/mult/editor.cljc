(ns cljctools.mult.editor
  (:refer-clojure :exclude [send])
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
   [clojure.spec.alpha :as s]))

(require '[cljctools.mult.editor.impl :as mult.editor.impl])

(s/def ::tab-id (s/or :uuid uuid? :string string?))
(s/def ::tab-title string?)
(s/def ::cmd-id string?)
(s/def ::cmd-ids (s/coll-of ::cmd-id))

(s/def ::filepath string?)
(s/def ::ns-symbol symbol?)

(s/def ::channel #?(:clj #(instance? clojure.core.async.impl.channels.ManyToManyChannel %)
                    :cljs #(instance? clojure.core.async.impl.channels/ManyToManyChannel %)))
(s/def ::mult #?(:clj #(satisfies? clojure.core.async.Mult %)
                 :cljs #(satisfies? clojure.core.async/Mult %)))

(s/def ::tab-recv| ::channel)
(s/def ::tab-evt| ::channel)
(s/def ::cmd| ::channel)

(defprotocol Release
  (release* [_]))

(defprotocol Editor
  (show-notification* [_ text])
  (register-commands* [_ opts])
  (active-text-editor* [_])
  (open-tab* [_ opts])
  #_Release
  #_IDeref)

(s/def ::editor #(and
                  (satisfies? Editor %)
                  (satisfies? Release %)
                  #?(:clj (satisfies? clojure.lang.IDeref %))
                  #?(:cljs (satisfies? cljs.core/IDeref %))))


(s/def ::range (s/tuple int? int? int? int?))

(defprotocol TextEditor
  (text* [_] [_ range])
  (filepath* [_]))

(s/def ::text-editor #(satisfies? TextEditor %))

(defprotocol Active?
  (active?* [_]))

(defprotocol Send
  (send* [_ data]))

(defprotocol Tab
  #_Send
  #_Active?
  #_Release
  #_IDeref)

(s/def ::tab #(and
               (satisfies? Tab %)
               (satisfies? Active? %)
               (satisfies? Send %)
               (satisfies? Release %)
               #?(:clj (satisfies? clojure.lang.IDeref %))
               #?(:cljs (satisfies? cljs.core/IDeref %))))



(defonce ^:private registryA (atom {}))
(defonce ^:private registry-tabsA (atom {}))

(def ^:const NS_DECLARATION_LINE_RANGE 100)

(s/def ::create-opts (s/keys :req []
                             :opt [::id]))

(defn create
  [{:as opts
    :keys [::id]
    :or {id (str #?(:clj  (java.util.UUID/randomUUID)
                    :cljs (random-uuid)))}}]
  {:pre [(s/assert ::create-opts opts)]
   :post [(s/assert ::editor %)]}
  (or
   (get @registryA id)
   (mult.editor.impl/create (merge
                             opts
                             {::id id}))))

(defmulti release
  "Releases editor instance"
  {:arglists '([id] [editor])} type)
(defmethod release :default
  [id]
  (when-let [editor (get @registryA id)]
    (close editor)))
(defmethod release ::editor
  [editor]
  {:pre [(s/assert ::editor editor)]}
  (release* editor)
  (swap! registryA dissoc (get @editor ::id)))

(defn show-notification
  [editor text]
  (show-notification* editor text))

(s/def ::register-commands-opts (s/keys :req [::cmd-ids
                                              ::cmd|]
                                        :opt []))

(defn register-commands
  [editor {:as opts
           :keys [::cmd-ids
                  ::cmd|]}]
  {:pre [(s/assert ::register-commands-opts opts)]}
  (register-commands* editor opts))

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
  (when-let [text-editor (active-text-editor* editor)]
    (let [range [[0 0] [NS_DECLARATION_LINE_RANGE 0]]
          text (text* text-editor range)
          filepath (filepath* text-editor)
          ns-symbol (parse-ns filepath text)
          data {::filepath active-document-filepath
                ::ns-symbol ns-symbol}]
      data
      #_(prn active-text-editor.document.languageId))))

(s/def ::open-tab-opts (s/keys :req []
                               :opt [::tab-id
                                     ::tab-title
                                     ::tab-recv|
                                     ::tab-evt|]))
(defn open-tab
  [editor
   {:as opts
    :keys [::tab-id
           ::tab-title
           ::tab-recv|
           ::tab-evt|]
    :or {tab-id (str #?(:clj  (java.util.UUID/randomUUID)
                        :cljs (random-uuid)))
         tab-title "Default title"
         tab-recv| (chan 10)
         tab-evt| (chan 10)}}]
  {:pre [(s/assert ::open-tab-opts opts)]
   :post [(s/assert ::tab %)]}
  (or (get @registry-tabsA id)
      (let [tab (open-tab* editor (merge
                                   opts
                                   {::tab-id tab-id
                                    ::tab-title tab-title
                                    ::tab-recv| tab-recv|
                                    ::tab-evt| tab-evt|}))]
        (swap! registry-tabsA assoc tab-id tab))))

(defmulti close-tab
  "Closes tab"
  {:arglists '([tab-id] [tab])} type)
(defmethod close-tab :default
  [tab-id]
  (when-let [tab (get @registry-tabsA tab-id)]
    (release tab)))
(defmethod close-tab ::tab
  [tab]
  {:pre [(s/assert ::tab tab)]}
  (release* tab)
  (swap! registry-tabsA dissoc (get @tab ::id)))

(defmulti send-tab
  "Send data to tab"
  {:arglists '([tab-id data] [tab data])} type)
(defmethod send-tab :default
  [tab-id data]
  (when-let [tab (get @registry-tabsA tab-id)]
    (send-tab tab data)))
(defmethod send-tab ::tab
  [tab data]
  {:pre [(s/assert ::tab tab)]}
  (send* tab data))