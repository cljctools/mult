(ns mult.impl.self-hosting
  (:require
   [clojure.core.async :as a :refer [chan go go-loop <! >!  take! put! offer! poll! alt! alts! close!
                                     pub sub unsub mult tap untap mix admix unmix
                                     timeout to-chan  sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [cljs.js :as cljs]
   [cljs.env :as env]
   [shadow.cljs.bootstrap.node :as boot]
   [cljs.nodejs :as node]))

(defonce fs (node/require "fs"))
(defonce path (node/require "path"))

(defonce compile-state-ref (env/default-compiler-env))

(defn eval-str [code ns]
  (let [c| (chan 1)]
    (cljs/eval-str
     compile-state-ref
     code
     "[test]"
     {:eval cljs/js-eval
      :ns ns
      :load (partial boot/load compile-state-ref)}
     (fn [result]
       (put! c| result #(close! c|))))
    c|))

(defn init
  [path-to-bootstrap]
  (let [c| (chan 1)]
    (boot/init compile-state-ref
               {:path path-to-bootstrap}
               (fn []
                 (prn "; boot/init initialized")
                 (let [eval cljs.core/*eval*]
                   (set! cljs.core/*eval*
                         (fn [form]
                           (binding [cljs.env/*compiler* compile-state-ref
                                     *ns* (find-ns cljs.analyzer/*cljs-ns*) #_(find-ns 'mult.extension)
                                     cljs.js/*eval-fn* cljs.js/js-eval]
                             (eval form)))))
                 (close! c|)))
    c|))

(comment

  (eval '(let [x 3]
           x))
  (eval '(let [x (fn [] 3)]
           x))
  (eval '(fn []))

  (def f (eval '(fn [file-uri] (cljs.core/re-matches #".+\.cljs" file-uri))))
  (f "abc.cljs")
  
  (eval '(re-matches #".+clj" "abc.clj"))
  

  ;;
  )