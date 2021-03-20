(ns cljctools.mult.spec
  (:require
   [clojure.spec.alpha :as s]
   [cljctools.mult.protocols :as mult.protocols]
   [cljctools.mult.fmt.spec :as mult.fmt.spec]
   [cljctools.mult.nrepl.spec :as mult.nrepl.spec]))

(s/def ::channel #?(:clj #(instance? clojure.core.async.impl.channels.ManyToManyChannel %)
                    :cljs #(instance? cljs.core.async.impl.channels/ManyToManyChannel %)))
(s/def ::mult #?(:clj #(satisfies? clojure.core.async.Mult %)
                 :cljs #(satisfies? cljs.core.async/Mult %)))


(s/def ::cljctools-mult #(and
                          (satisfies? mult.protocols/CljctoolsMult %)
                          #?(:clj (satisfies? clojure.lang.IDeref %))
                          #?(:cljs (satisfies? cljs.core/IDeref %))))

(s/def ::nrepl-id keyword?)

(s/def ::include-file? (s/or
                        ::ifn? ifn?
                        ::list? list?))

(s/def ::nrepl-meta (s/merge
                     ::mult.nrepl.spec/create-nrepl-connection-opts
                     (s/keys :req [::nrepl-id
                                   ::include-file?])))

(s/def ::nrepl-metas (s/coll-of ::nrepl-meta :into #{}))

(s/def ::open-n-tabs-on-start int?)
(s/def ::config (s/keys :req [::nrepl-metas
                              ::open-n-tabs-on-start]))

(s/def ::cmd| ::channel)
(s/def ::cmd #{::cmd-open
               ::cmd-ping
               ::cmd-eval})

(s/def ::op| ::channel)
(s/def ::op #{::op-ping
              ::op-eval
              ::op-update-ui-state
              ::op-did-change-active-text-editor
              ::op-select-tab})

(s/def ::eval-result (s/nilable string?))

(s/def ::ui-state (s/keys :req []
                          :opt [::eval-result
                                ::mult.fmt.spec/ns-symbol
                                ::config
                                ::nrepl-id]))

(s/def ::ns-symbol symbol?)




