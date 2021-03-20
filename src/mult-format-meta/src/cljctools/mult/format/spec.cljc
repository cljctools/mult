(ns cljctools.mult.format.spec
  (:require
   [clojure.spec.alpha :as s]
   [cljctools.mult.format.protocols :as mult.format.protocols]))

(s/def ::ns-symbol symbol?)

(s/def ::channel #?(:clj #(instance? clojure.core.async.impl.channels.ManyToManyChannel %)
                    :cljs #(instance? cljs.core.async.impl.channels/ManyToManyChannel %)))
(s/def ::mult #?(:clj #(satisfies? clojure.core.async.Mult %)
                 :cljs #(satisfies? cljs.core.async/Mult %)))


(s/def ::fmt #(and
               (satisfies? mult.format.protocols/Fmt %)
               (satisfies? mult.format.protocols/Release %)
               #?(:clj (satisfies? clojure.lang.IDeref %))
               #?(:cljs (satisfies? cljs.core/IDeref %))))


(s/def ::cmd| ::channel)
(s/def ::cmd #{::cmd-format-current-form})

(s/def ::op| ::channel)
(s/def ::op #{})
