(ns cljctools.mult.ui.spec
  #?(:cljs (:require-macros [cljctools.mult.ui.spec]))
  (:require
   [clojure.spec.alpha :as s]))

(s/def ::page-events keyword?)
(s/def ::page-game keyword?)
(s/def ::scenario-origin string?)
