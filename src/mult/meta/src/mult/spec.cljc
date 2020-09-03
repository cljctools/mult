(ns mult.spec
  #?(:cljs (:require-macros [mult.spec]))
  (:require
   [clojure.spec.alpha :as s]
   [mult.conf.spec :as mult.conf.spec]))

(do (clojure.spec.alpha/check-asserts true))

(s/def ::uuid uuid?)

(s/def ::filepath string?)

(s/def ::eval-result any?)

(s/def ::state (s/keys ::req [::mult.conf.spec/mult-edn
                              ::eval-result]))

(def cmd-ids #{"mult.open"
               "mult.ping"
               "mult.eval"})

(defmacro assert-cmd-id
  [cmd-id]
  (s/assert cmd-ids cmd-id)
  `~cmd-id)