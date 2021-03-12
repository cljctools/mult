(ns cljctools.mult.editor.tabapp.core
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
   [cljctools.mult.editor.tabapp.impl :as editor.tabapp.impl]))

(defonce ^:private registryA (atom {}))

(defn create
  [{:as opts
    :keys [::editor.spec/id]
    :or {id (str #?(:clj  (java.util.UUID/randomUUID)
                    :cljs (random-uuid)))}}]
  {:pre [(s/assert ::editor.spec/create-tabapp-opts opts)]
   :post [(s/assert ::editor.spec/tabapp %)]}
  (or
   (get @registryA id)
   (let [tabapp (editor.tabapp.impl/create
                 (merge
                  opts
                  {::editor.spec/id id}))]
     (swap! registryA assoc id tabapp)
     tabapp)))

(defmulti release
  "Releases tabapp instance"
  {:arglists '([id] [tabapp])} type)
(defmethod release :default
  [id]
  (when-let [tabapp (get @registryA id)]
    (release tabapp)))
(defmethod release ::editor.spec/tabapp
  [tabapp]
  {:pre [(s/assert ::editor.spec/tabapp tabapp)]}
  (editor.protocols/release* tabapp)
  (swap! registryA dissoc (get @tabapp ::editor.spec/id)))

(defmulti send
  "Send data to extension"
  {:arglists '([id msg] [tabapp msg])} type)
(defmethod send :default
  [id msg]
  (when-let [tabapp (get @registryA id)]
    (send tabapp msg)))
(defmethod send ::editor.spec/tabapp
  [tabapp msg]
  {:pre [(s/assert ::editor.spec/tabapp tabapp)]}
  (editor.protocols/send* tabapp msg))