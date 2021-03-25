(ns cljctools.mult.edit.spec
  (:require
   [clojure.spec.alpha :as s]
   [clojure.core.async]
   [cljctools.mult.edit.protocols :as mult.edit.protocols]))


(s/def ::channel #?(:clj #(instance? clojure.core.async.impl.channels.ManyToManyChannel %)
                    :cljs #(instance? cljs.core.async.impl.channels/ManyToManyChannel %)))
(s/def ::mult #?(:clj #(satisfies? clojure.core.async.Mult %)
                 :cljs #(satisfies? cljs.core.async/Mult %)))

(s/def ::edit #(and
                (satisfies? mult.edit.protocols/Edit %)
                (satisfies? mult.edit.protocols/Release %)
                #?(:clj (satisfies? clojure.lang.IDeref %))
                #?(:cljs (satisfies? cljs.core/IDeref %))))

(s/def ::zloc any?)

(s/def ::clj-string string?)

(s/def ::position (s/tuple int? int?))

(s/def ::cursor-position ::position)


(s/def ::evt| ::channel)
(s/def ::evt|mult ::mult)
(s/def ::evt #{})

(s/def ::cmd| ::channel)
(s/def ::cmd #{::cmd-format-current-form
               ::cmd-select-current-form})

(s/def ::ops| ::channel)
(s/def ::ops #{})

(s/def ::op (s/merge
             ::evt
             ::cmd
             ::ops))