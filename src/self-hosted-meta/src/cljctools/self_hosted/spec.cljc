(ns cljctools.self-hosted.spec
  (:require
   [clojure.spec.alpha :as s]
   [cljctools.self-hosted.protocols :as self-hosted.protocols]))

(s/def ::code-str string?)
(s/def ::ns-symbol symbol?)

(s/def ::compile-state-ref some?)

(s/def ::compiler #(and
                    (satisfies? self-hosted.protocols/Compiler %)
                    (satisfies? self-hosted.protocols/Release %)
                    #?(:clj (satisfies? clojure.lang.IDeref %))
                    #?(:cljs (satisfies? cljs.core/IDeref %))))
