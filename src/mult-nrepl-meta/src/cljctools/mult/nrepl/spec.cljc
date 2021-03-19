(ns cljctools.mult.editor.spec
  (:require
   [clojure.spec.alpha :as s]
   [cljctools.mult.nrepl.protocols :as mult.nrepl.protocols]))

(s/def ::host string?)
(s/def ::port int?)

(s/def ::channel #?(:clj #(instance? clojure.core.async.impl.channels.ManyToManyChannel %)
                    :cljs #(instance? cljs.core.async.impl.channels/ManyToManyChannel %)))
(s/def ::mult #?(:clj #(satisfies? clojure.core.async.Mult %)
                 :cljs #(satisfies? cljs.core.async/Mult %)))


(s/def ::cmd| ::channel)
(s/def ::evt| ::channel)

(s/def ::cmd|mult ::mult)
(s/def ::evt|mult ::mult)

(s/def ::op #{})

(s/def ::nrepl-connection #(and
                            (satisfies? mult.nrepl.protocols/NreplConnection %)
                            (satisfies? mult.nrepl.protocols/Release %)
                            #?(:clj (satisfies? clojure.lang.IDeref %))
                            #?(:cljs (satisfies? cljs.core/IDeref %))))

(s/def ::connect-opts (s/keys :req [::host
                                    ::port]
                              :opt []))

(s/def ::create-nrepl-connection-opts (s/and
                                       ::connect-opts
                                       (s/keys :req []
                                               :opt [])))