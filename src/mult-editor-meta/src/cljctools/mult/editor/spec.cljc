(ns cljctools.mult.editor.spec
  (:require
   [clojure.core.async]
   [clojure.spec.alpha :as s]
   [cljctools.mult.editor.protocols :as editor.protocols]))

(s/def ::id keyword?)
(s/def ::tab-id string?)
(s/def ::tab-title string?)
(s/def ::cmd-id string?)
(s/def ::cmd-ids (s/coll-of ::cmd-id))

(s/def ::filepath string?)
(s/def ::ns-symbol symbol?)

(s/def ::channel #?(:clj #(instance? clojure.core.async.impl.channels.ManyToManyChannel %)
                    :cljs #(instance? cljs.core.async.impl.channels/ManyToManyChannel %)))
(s/def ::mult #?(:clj #(satisfies? clojure.core.async.Mult %)
                 :cljs #(satisfies? cljs.core.async/Mult %)))

(s/def ::cmd| ::channel)

(s/def ::on-tab-closed ifn?)
(s/def ::on-tab-message ifn?)


(s/def ::editor #(and
                  (satisfies? editor.protocols/Editor %)
                  (satisfies? editor.protocols/Release %)
                  #?(:clj (satisfies? clojure.lang.IDeref %))
                  #?(:cljs (satisfies? cljs.core/IDeref %))))

(s/def ::text-editor #(satisfies? editor.protocols/TextEditor %))

(s/def ::range (s/tuple int? int? int? int?))

(s/def ::tab #(and
               (satisfies? editor.protocols/Tab %)
               (satisfies? editor.protocols/Open %)
               (satisfies? editor.protocols/Close %)
               (satisfies? editor.protocols/Send %)
               (satisfies? editor.protocols/Active? %)
               (satisfies? editor.protocols/Release %)
               #?(:clj (satisfies? clojure.lang.IDeref %))
               #?(:cljs (satisfies? cljs.core/IDeref %))))

(s/def ::create-opts (s/keys :req []
                             :opt [::id]))

(s/def ::register-commands-opts (s/keys :req [::cmd-ids
                                              ::cmd|]
                                        :opt []))

(s/def ::create-tab-opts (s/keys :req []
                                 :opt [::tab-id
                                       ::tab-title
                                       ::on-tab-closed
                                       ::on-tab-message]))


(s/def ::tabapp #(and
                  (satisfies? editor.protocols/TabApp %)
                  (satisfies? editor.protocols/Send %)
                  (satisfies? editor.protocols/Release %)
                  #?(:clj (satisfies? clojure.lang.IDeref %))
                  #?(:cljs (satisfies? cljs.core/IDeref %))))

(s/def ::on-message ifn?)

(s/def ::create-tabapp-opts (s/keys :req [::on-message]
                                    :opt [::id]))