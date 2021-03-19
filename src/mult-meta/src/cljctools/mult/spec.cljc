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

(s/def ::tab-id keyword?)
(s/def ::nrepl-ids (s/coll-of ::mult.nrepl.spec/nrepl-id :into #{}))

(s/def ::tab-meta (s/keys :req [::tab-id
                                        ::nrepl-ids]))
(s/def ::tab-metas (s/coll-of ::tab-meta :into #{}))

(s/def ::open-tab-ids (s/coll-of ::tab-id :into #{}))
(s/def ::active-tab-id ::tab-id)

(s/def ::config (s/keys :req [::connection-metas
                              ::mult.nrepl.spec/nrepl-metas
                              ::tab-metas
                              ::open-tab-ids
                              ::active-tab-id]))

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
                                ::active-tab-id
                                ::config
                                ::nrepl-id]))


