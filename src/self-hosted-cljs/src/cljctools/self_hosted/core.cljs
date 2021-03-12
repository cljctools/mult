(ns cljctools.self-hosted.core
  (:require
   [clojure.core.async :as a :refer [chan go go-loop <! >! take! put! offer! poll! alt! alts! close!
                                     pub sub unsub mult tap untap mix admix unmix pipe
                                     timeout to-chan  sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [clojure.string]
   [clojure.pprint :refer [pprint]]
   [cljs.reader :refer [read-string]]
   [goog.string.format]
   [goog.string :refer [format]]
   [clojure.spec.alpha :as s]

   [cljs.js :as cljs]
   [cljs.analyzer :as ana]
   [cljs.tools.reader :as r]
   [cljs.tools.reader.reader-types :refer [string-push-back-reader]]
   [cljs.tagged-literals :as tags]

   [cljctools.self-hosted.spec :as self-hosted.spec]
   [cljctools.self-hosted.protocols :as self-hosted.protocols]))

(s/def ::init-opts (s/keys :req []
                           :opt []))

(defn create-compiler
  []
  {:post [(s/assert ::self-hosted.spec/compiler %)]}
  (let [stateA (atom nil)
        compile-state-ref (cljs/empty-state)
        compiler
        ^{:type ::self-hosted.spec/compiler}
        (reify
          self-hosted.protocols/Compiler
          (init*
            [_ opts]
            {:pre [(s/assert ::init-opts opts)]}
            (prn ::init)
            (go
              (let [eval cljs.core/*eval*]
                (set! cljs.core/*eval*
                      (fn [form]
                        (binding [cljs.env/*compiler* compile-state-ref
                                  *ns* #_(find-ns cljs.analyzer/*cljs-ns*) (find-ns 'deathstar.extension)
                                  cljs.js/*eval-fn* cljs.js/js-eval]
                          (eval form)))))))
          (eval-data*
            [_ opts])
          (eval-str*
            [_ opts])
          (compile-js-str*
            [_ opts]))]
    (reset! stateA {::self-hosted.spec/compile-state-ref compile-state-ref})
    compiler))

(defn init
  [compiler opts]
  (self-hosted.protocols/init* compiler opts))

(defn eval-data
  [compiler opts]
  (self-hosted.protocols/eval-data* compiler opts))

(defn eval-str
  [compiler opts]
  (self-hosted.protocols/eval-str* compiler opts))

(defn compile-js-str
  [compiler opts]
  (self-hosted.protocols/compile-js-str* compiler opts))