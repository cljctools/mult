(ns cljctools.mult.editor.spec
  (:require
   [clojure.spec.alpha :as s]
   [cljctools.mult.editor.protocols :as mult.editor.protocols]))

(s/def ::tab-id string?)
(s/def ::tab-title string?)

(s/def ::filepath string?)

(s/def ::channel #?(:clj #(instance? clojure.core.async.impl.channels.ManyToManyChannel %)
                    :cljs #(instance? cljs.core.async.impl.channels/ManyToManyChannel %)))
(s/def ::mult #?(:clj #(satisfies? clojure.core.async.Mult %)
                 :cljs #(satisfies? cljs.core.async/Mult %)))


(s/def ::cmd| ::channel)
(s/def ::evt| ::channel)

(s/def ::cmd|mult ::mult)
(s/def ::evt|mult ::mult)

(s/def ::op #{::evt-did-change-active-text-editor})

(s/def ::on-tab-closed ifn?)
(s/def ::on-tab-message ifn?)

(s/def ::editor #(and
                  (satisfies? mult.editor.protocols/Editor %)
                  (satisfies? mult.editor.protocols/Release %)
                  #?(:clj (satisfies? clojure.lang.IDeref %))
                  #?(:cljs (satisfies? cljs.core/IDeref %))))

(s/def ::text-editor #(satisfies? mult.editor.protocols/TextEditor %))

(s/def ::range (s/tuple int? int? int? int?))

(s/def ::tab #(and
               (satisfies? mult.editor.protocols/Tab %)
               (satisfies? mult.editor.protocols/Open %)
               (satisfies? mult.editor.protocols/Close %)
               (satisfies? mult.editor.protocols/Send %)
               (satisfies? mult.editor.protocols/Active? %)
               (satisfies? mult.editor.protocols/Release %)
               #?(:clj (satisfies? clojure.lang.IDeref %))
               #?(:cljs (satisfies? cljs.core/IDeref %))))