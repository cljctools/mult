(ns cljctools.mult.fmt.spec
  (:require
   [clojure.spec.alpha :as s]
   [cljctools.mult.fmt.protocols :as mult.fmt.protocols]))

(s/def ::ns-symbol symbol?)

(s/def ::channel #?(:clj #(instance? clojure.core.async.impl.channels.ManyToManyChannel %)
                    :cljs #(instance? cljs.core.async.impl.channels/ManyToManyChannel %)))
(s/def ::mult #?(:clj #(satisfies? clojure.core.async.Mult %)
                 :cljs #(satisfies? cljs.core.async/Mult %)))


(s/def ::fmt #(and
               (satisfies? mult.fmt.protocols/Fmt %)
               (satisfies? mult.fmt.protocols/Release %)
               #?(:clj (satisfies? clojure.lang.IDeref %))
               #?(:cljs (satisfies? cljs.core/IDeref %))))


(s/def ::cmd| ::channel)
(s/def ::cmd #{::cmd-format-current-form})

(s/def ::op| ::channel)
(s/def ::op #{})

(s/def ::op-value (s/keys :req-un [::op]
                          :opt []))
