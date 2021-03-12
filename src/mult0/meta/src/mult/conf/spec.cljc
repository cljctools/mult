(ns mult.conf.spec
  #?(:cljs (:require-macros [mult.conf.spec]))
  (:require
   [clojure.spec.alpha :as s]))

(do (clojure.spec.alpha/check-asserts true))


(s/def ::conn-id keyword?)
(s/def ::repl-id keyword?)
(s/def ::tab-id keyword?)

(s/def ::host string?)
(s/def ::port int?)
(s/def ::conn-type #{:nrepl})
(s/def ::connection (s/keys :req [::host
                                  ::port
                                  ::conn-type]))
(s/def ::connections (s/map-of ::conn-id ::connection))


(s/def ::repl-type #{:nrepl :shadow-cljs})
(s/def ::runtime #{:clj :cljs})
(s/def ::shadow-build-key keyword?)
(s/def ::include-file? some?)
(s/def ::repl (s/keys :req [::conn-id
                            ::repl-type
                            ::runtime]
                      :opt [::include-file?
                            ::shadow-build-key]))
(s/def ::repls (s/map-of ::repl-id ::repl))


(s/def ::repl-ids (s/coll-of ::repl-id))

(s/def ::tab (s/keys :req [::repl-ids]))
(s/def ::tabs (s/map-of ::tab-id ::tab))

(s/def ::open-tabs (s/coll-of keyword?))
(s/def ::active-tab keyword?)

(s/def ::mult-edn (s/keys :req [::connections
                                ::repls
                                ::tabs
                                ::open-tabs
                                ::active-tab]))