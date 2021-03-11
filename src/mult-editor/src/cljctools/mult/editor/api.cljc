(ns cljctools.mult.editor.api
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
   [clojure.spec.alpha :as s]
   [cljctools.mult.editor.protocols :as editor.protocols]
   [cljctools.mult.editor.spec :as editor.spec]
   [cljctools.mult.editor.impl :as editor.impl]))

(defonce ^:private registryA (atom {}))
(defonce ^:private registry-tabsA (atom {}))
(defonce ^:private registry-tabappsA (atom {}))

(def ^:const NS_DECLARATION_LINE_RANGE 100)

(defn create
  [{:as opts
    :keys [::editor.spec/id]
    :or {id (str #?(:clj  (java.util.UUID/randomUUID)
                    :cljs (random-uuid)))}}]
  {:pre [(s/assert ::editor.spec/create-opts opts)]
   :post [(s/assert ::editor.spec/editor %)]}
  (or
   (get @registryA id)
   (let [editor (editor.impl/create
                 (merge
                  opts
                  {::editor.spec/id id}))]
     (swap! registryA assoc id editor)
     editor)))

(defmulti release
  "Releases editor instance"
  {:arglists '([id] [editor])} type)
(defmethod release :default
  [id]
  (when-let [editor (get @registryA id)]
    (release editor)))
(defmethod release ::editor.spec/editor
  [editor]
  {:pre [(s/assert ::editor.spec/editor editor)]}
  (editor.protocols/release* editor)
  (swap! registryA dissoc (get @editor ::editor.spec/id)))

(defn show-notification
  [editor text]
  (editor.protocols/show-notification* editor text))

(defn register-commands
  [editor {:as opts
           :keys [::editor.spec/cmd-ids
                  ::editor.spec/cmd|]}]
  {:pre [(s/assert ::editor.spec/register-commands-opts opts)]}
  (editor.protocols/register-commands* editor opts))

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
  (when-let [text-editor (editor.protocols/active-text-editor* editor)]
    (let [range [[0 0] [NS_DECLARATION_LINE_RANGE 0]]
          text (editor.protocols/text* text-editor range)
          filepath (editor.protocols/filepath* text-editor)
          ns-symbol (parse-ns filepath text)
          data {::editor.spec/filepath filepath
                ::editor.spec/ns-symbol ns-symbol}]
      data
      #_(prn active-text-editor.document.languageId))))

(defn create-tab
  [editor
   {:as opts
    :keys [::editor.spec/tab-id
           ::editor.spec/tab-title
           ::editor.spec/on-tab-closed
           ::editor.spec/on-tab-message]
    :or {tab-id (str #?(:clj  (java.util.UUID/randomUUID)
                        :cljs (random-uuid)))
         tab-title "Default title"}}]
  {:pre [(s/assert ::editor.spec/create-tab-opts opts)]
   :post [(s/assert ::editor.spec/tab %)]}
  (or (get @registry-tabsA tab-id)
      (let [tab (editor.protocols/create-tab*
                 editor (merge
                         opts
                         {::tab-id tab-id
                          ::tab-title tab-title}))]
        (swap! registry-tabsA assoc tab-id tab)
        tab)))

(defmulti release-tab
  "Releases tab instance"
  {:arglists '([tab-id] [tab])} type)
(defmethod release-tab :default
  [tab-id]
  (when-let [tab (get @registry-tabsA tab-id)]
    (release-tab tab)))
(defmethod release-tab ::tab
  [tab]
  {:pre [(s/assert ::tab tab)]}
  (editor.protocols/release* tab)
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
  (editor.protocols/send* tab data))
