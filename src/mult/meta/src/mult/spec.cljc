(ns mult.spec
  #?(:cljs (:require-macros [mult.spec]))
  (:require
   [clojure.spec.alpha :as s]
   [mult.conf.spec]))

(do (clojure.spec.alpha/check-asserts true))

(s/def ::uuid uuid?)

(s/def ::filepath string?)

(s/def ::eval-result any?)

(s/def ::state (s/keys ::req [::mult.conf.spec/mult-edn
                              ::eval-result]))
