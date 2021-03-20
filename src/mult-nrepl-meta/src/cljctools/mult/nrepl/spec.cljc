(ns cljctools.mult.nrepl.spec
  (:require
   [clojure.spec.alpha :as s]
   [cljctools.mult.nrepl.protocols :as mult.nrepl.protocols]))


(s/def ::channel #?(:clj #(instance? clojure.core.async.impl.channels.ManyToManyChannel %)
                    :cljs #(instance? cljs.core.async.impl.channels/ManyToManyChannel %)))
(s/def ::mult #?(:clj #(satisfies? clojure.core.async.Mult %)
                 :cljs #(satisfies? cljs.core.async/Mult %)))


(s/def ::cmd| ::channel)
(s/def ::evt| ::channel)

(s/def ::cmd|mult ::mult)
(s/def ::evt|mult ::mult)


(s/def ::host string?)
(s/def ::port int?)
(s/def ::nrepl-type #{:shadow-cljs :nrepl})
(s/def ::runtime #{:cljs :clj})
(s/def ::shadow-build-key keyword?)

(s/def ::op #{})

(s/def ::nrepl-connection #(and
                            (satisfies? mult.nrepl.protocols/NreplConnection %)
                            (satisfies? mult.nrepl.protocols/Release %)
                            #?(:clj (satisfies? clojure.lang.IDeref %))
                            #?(:cljs (satisfies? cljs.core/IDeref %))))

(s/def ::create-nrepl-connection-opts (s/keys :req [::host
                                                    ::port
                                                    ::nrepl-type
                                                    ::runtime]
                                              :opt [::shadow-build-key]))

(s/def ::session-id string?)
(s/def ::code-string string?)
(s/def ::ns-symbol symbol?)

(s/def ::eval-opts (s/keys :req [::code-string]
                           :opt [::session-id]))

(s/def ::clone-opts (s/keys :req []
                            :opt [::session-id]))