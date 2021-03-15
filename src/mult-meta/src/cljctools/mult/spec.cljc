(ns cljctools.mult.spec
  (:require
   [clojure.spec.alpha :as s]
   [cljctools.mult.protocols :as mult.protocols]
   [cljctools.mult.fmt.spec :as mult.fmt.spec]
   [cljctools.socket.spec :as socket.spec]))

(s/def ::channel #?(:clj #(instance? clojure.core.async.impl.channels.ManyToManyChannel %)
                    :cljs #(instance? cljs.core.async.impl.channels/ManyToManyChannel %)))
(s/def ::mult #?(:clj #(satisfies? clojure.core.async.Mult %)
                 :cljs #(satisfies? cljs.core.async/Mult %)))


(s/def ::cljctools-mult #(and
                          (satisfies? mult.protocols/CljctoolsMult %)
                          #?(:clj (satisfies? clojure.lang.IDeref %))
                          #?(:cljs (satisfies? cljs.core/IDeref %))))

(s/def ::logical-repl #(and
                        (satisfies? mult.protocols/LogicalRepl %)
                        (satisfies? mult.protocols/Release %)
                        #?(:clj (satisfies? clojure.lang.IDeref %))
                        #?(:cljs (satisfies? cljs.core/IDeref %))))

(s/def ::code-string string?)
(s/def ::logical-repl-eval-opts (s/keys :req [::code-string
                                              ::ns-symbol]))

(s/def ::connection-id keyword?)
(s/def ::repl-protocol #{:nrepl})
(s/def ::connection-opts-type #{::socket.spec/tcp-socket-opts
                                ::socket.spec/websocket-opts})
(s/def ::connection-opts (s/or
                          ::socket.spec/tcp-socket-opts ::socket.spec/tcp-socket-opts
                          ::socket.spec/websocket-opts ::socket.spec/websocket-opts))

(s/def ::connection-meta (s/keys :req [::connection-id
                                       ::repl-protocol
                                       ::connection-opts
                                       ::connection-opts-type]))

(s/def ::connection-metas (s/coll-of ::connection-meta :into #{}))

(s/def ::logical-repl-id keyword?)
(s/def ::logical-repl-type #{:shadow-cljs :nrepl})
(s/def ::runtime #{:cljs :clj})
(s/def ::shadow-build-key keyword?)
(s/def ::include-file? (s/or
                        ::ifn? ifn?
                        ::list? list?))

(s/def ::logical-repl-meta (s/keys :req [::logical-repl-id
                                         ::connection-id
                                         ::logical-repl-type
                                         ::runtime
                                         ::include-file?]
                                   :opt [::shadow-build-key]))

(s/def ::logical-repl-metas (s/coll-of ::logical-repl-meta :into #{}))

(s/def ::logical-tab-id keyword?)
(s/def ::logical-repl-ids (s/coll-of ::logical-repl-id :into #{}))

(s/def ::logical-tab-meta (s/keys :req [::logical-tab-id
                                        ::logical-repl-ids]))
(s/def ::logical-tab-metas (s/coll-of ::logical-tab-meta :into #{}))

(s/def ::open-logical-tab-ids (s/coll-of ::logical-tab-id :into #{}))
(s/def ::active-logical-tab-id ::logical-tab-id)

(s/def ::config (s/keys :req [::connection-metas
                              ::logical-repl-metas
                              ::logical-tab-metas
                              ::open-logical-tab-ids
                              ::active-logical-tab-id]))

(s/def ::cmd| ::channel)
(s/def ::cmd #{::cmd-open
               ::cmd-ping
               ::cmd-eval})

(s/def ::op| ::channel)
(s/def ::op #{::op-ping
              ::op-eval
              ::op-update-ui-state
              ::op-did-change-active-text-editor
              ::op-select-logical-tab})

(s/def ::eval-result (s/nilable string?))

(s/def ::op-value (s/keys :req-un [::op]
                          :opt []))

(s/def ::ui-state (s/keys :req []
                          :opt [::eval-result
                                ::mult.fmt.spec/ns-symbol
                                ::active-logical-tab-id
                                ::config
                                ::logical-repl-id]))


