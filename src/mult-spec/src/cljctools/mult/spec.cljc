(ns cljctools.mult.spec
  (:require
   [clojure.spec.alpha :as s]))

(s/def ::foo string?)
(s/def ::bar string?)