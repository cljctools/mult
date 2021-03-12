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
   [cljs.env :as env]
   [shadow.cljs.bootstrap.node :as boot]
   [cljctools.self-hosted.spec :as self-hosted.spec]
   [cljctools.self-hosted.protocols :as self-hosted.protocols]))

(defonce fs (js/require "fs"))
(defonce path (js/require "path"))

(s/def ::path string?)
(s/def ::load-on-init some?)

(s/def ::init-opts (s/keys :req-un [::path
                                    ::load-on-init]
                           :opt-un []))

(s/def ::eval-str-opts (s/keys :req [::self-hosted.spec/code-str
                                     ::self-hosted.spec/ns-symbol]
                               :opt []))

(defn create-compiler
  []
  {:post [(s/assert ::self-hosted.spec/compiler %)]}
  (let [stateA (atom nil)
        compile-state-ref (env/default-compiler-env)
        compiler
        ^{:type ::self-hosted.spec/compiler}
        (reify
          self-hosted.protocols/Compiler
          (init*
            [_ opts]
            {:pre [(s/assert ::init-opts opts)]}
            (prn ::init)
            (let [result| (chan 1)]
              (boot/init
               compile-state-ref
               opts
               (fn []
                 (prn ::boot-initialized)
                 (let [eval cljs.core/*eval*]
                   (set! cljs.core/*eval*
                         (fn [form]
                           (binding [cljs.env/*compiler* compile-state-ref
                                     *ns* (find-ns cljs.analyzer/*cljs-ns*) #_(find-ns 'mult.extension)
                                     cljs.js/*eval-fn* cljs.js/js-eval]
                             (eval form)))))
                 (close! result|)))
              result|))
          (eval-data*
            [_ opts])
          (eval-str*
            [_ opts]
            {:pre [(s/assert ::eval-str-opts opts)]}
            (let [{:keys [::self-hosted.spec/code-str
                          ::self-hosted.spec/ns-symbol]} opts
                  result| (chan 1)]
              (cljs/eval-str
               compile-state-ref
               code-str
               "[test]"
               {:eval cljs/js-eval
                :ns ns-symbol
                :load (partial boot/load compile-state-ref)}
               (fn [result]
                 (put! result| result #(close! result|))))
              result|))
          (compile-js-str*
            [_ opts])

          self-hosted.protocols/Release
          (release*
            [_]
            (do nil))

          cljs.core/IDeref
          (-deref [_] @stateA))]
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