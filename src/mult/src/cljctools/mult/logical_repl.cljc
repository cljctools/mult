(ns cljctools.mult.logical-repl
  (:require
   [clojure.core.async :as a :refer [chan go go-loop <! >! take! put! offer! poll! alt! alts! close!
                                     pub sub unsub mult tap untap mix admix unmix pipe
                                     timeout to-chan  sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [clojure.string]
   [clojure.pprint :refer [pprint]]
   #?(:cljs [cljs.reader :refer [read-string]])
   #?(:cljs [goog.string.format])
   #?(:cljs [goog.string :refer [format]])
   [clojure.spec.alpha :as s]

   [cljctools.mult.nrepl.spec :as mult.nrepl.spec]
   [cljctools.mult.nrepl.protocols :as mult.nrepl.protocols]

   [cljctools.mult.spec :as mult.spec]
   [cljctools.mult.fmt.spec :as mult.fmt.spec]
   [cljctools.mult.protocols :as mult.protocols]))

(s/def ::recv| ::mult.spec/channel)
(s/def ::recv|mult ::mult.spec/mult)
(s/def ::send| ::mult.spec/channel)

(s/def ::opts (s/keys :req []
                      :opt []))

(s/def ::create-opts (s/and
                      ::opts
                      ::mult.spec/logical-repl-meta
                      (s/keys :req []
                              :opt [])))

(s/def ::create-nrepl-opts (s/and
                            ::opts
                            (s/keys :req []
                                    :opt [])))

(s/def ::create-shadow-cljs-opts (s/and
                                  ::opts
                                  (s/keys :req [::mult.spec/shadow-build-key]
                                          :opt [])))

(declare create-nrepl
         create-shadow-clj
         create-shadow-cljs)

(defn create
  [{:as opts
    :keys [::mult.spec/logical-repl-type
           ::mult.spec/runtime]
    :or {}}]
  {:pre [(s/assert ::create-opts opts)]
   :post [(s/assert ::mult.spec/logical-repl %)]}
  (let []
    (cond
      (= [logical-repl-type]
         [:nrepl])
      (create-nrepl (merge opts {}))

      (= [logical-repl-type runtime]
         [:shadow-cljs :clj])
      (create-nrepl (merge opts {}))

      (= [logical-repl-type runtime]
         [:shadow-cljs :cljs])
      (create-shadow-cljs (merge opts {}))

      :else (throw (ex-info "No ::mult.spec/logical-repl for provided opts"  opts)))))

(defn create-nrepl
  [{:as opts
    :keys []}]
  {:pre [(s/assert ::create-nrepl-opts opts)]
   :post [(s/assert ::mult.spec/logical-repl %)]}
  (let [stateA (atom nil)
        recv| (chan (sliding-buffer 10))
        send| (chan (sliding-buffer 10))
        recv|mult (mult recv|)

        eval-fn
        (fn eval-fn
          [code-string]
          (mult.nrepl.protocols/eval*
           nil
           {::mult.nrepl.spec/send| send|
            ::mult.nrepl.spec/recv|mult recv|mult
            ::mult.nrepl.spec/session (get @stateA ::mult.nrepl.spec/session)
            ::mult.nrepl.spec/code code-string}))

        init-fn
        (fn init-fn
          []
          (go
            (let [{:keys [new-session]} (<! (mult.nrepl.protocols/clone*
                                             nil
                                             {::mult.nrepl.spec/send| send|
                                              ::mult.nrepl.spec/recv|mult recv|mult}))]
              (swap! stateA assoc ::mult.nrepl.spec/session new-session))))

        logical-repl
        ^{:type ::mult.spec/logical-repl}
        (reify
          mult.protocols/LogicalRepl
          (eval*
            [_ {:as opts
                :keys [::mult.spec/code-string
                       ::mult.fmt.spec/ns-symbol]}]
            {:pre [(s/assert ::mult.spec/logical-repl-eval-opts opts)]}
            (go
              (when-not (get @stateA ::mult.nrepl.spec/session)
                (<! (init-fn)))
              (let [code-string-formatted
                    (format
                     "(do (in-ns '%s) %s)" ns-symbol code-string)]
                (<! (eval-fn code-string-formatted)))))
          mult.protocols/Release
          (release*
            [_]
            (close! recv|)
            (close! send|))
          #?(:clj clojure.lang.IDeref)
          #?(:clj (deref [_] @stateA))
          #?(:cljs cljs.core/IDeref)
          #?(:cljs (-deref [_] @stateA)))]
    (reset! stateA (merge opts
                          {::opts opts
                           ::mult.nrepl.spec/session nil
                           ::recv| recv|
                           ::recv|mult (mult recv|)
                           ::send| send|}))
    logical-repl))


(defn create-shadow-cljs
  [{:as opts
    :keys [::mult.spec/shadow-build-key]}]
  {:pre [(s/assert ::create-shadow-cljs-opts opts)]
   :post [(s/assert ::mult.spec/logical-repl %)]}
  (let [stateA (atom nil)
        recv| (chan (sliding-buffer 10))
        send| (chan (sliding-buffer 10))
        recv|mult (mult recv|)

        eval-fn
        (fn eval-fn
          [code-string]
          (mult.nrepl.protocols/eval*
           nil
           {::mult.nrepl.spec/send| send|
            ::mult.nrepl.spec/recv|mult recv|mult
            ::mult.nrepl.spec/session (get @stateA ::mult.nrepl.spec/session)
            ::mult.nrepl.spec/code code-string}))

        init-fn
        (fn init-fn
          []
          (go
            (let [{:keys [new-session] :as response} (<! (mult.nrepl.protocols/clone*
                                                          nil
                                                          {::mult.nrepl.spec/send| send|
                                                           ::mult.nrepl.spec/recv|mult recv|mult}))]
              (swap! stateA assoc ::mult.nrepl.spec/session new-session))))

        back-to-clj-fn
        (fn back-to-clj-fn
          []
          (go
            (let [code-string-formatted ":cljs/quit"]
              (<! (eval-fn code-string-formatted)))))

        select-build-fn
        (fn select-build-fn
          []
          (go
            (let [code-string-formatted
                  (format "(shadow.cljs.devtools.api/nrepl-select %s)" shadow-build-key)]
              (<! (eval-fn code-string-formatted)))))

        logical-repl
        ^{:type ::mult.spec/logical-repl}
        (reify
          mult.protocols/LogicalRepl
          (on-activate*
            [_ ns-symbol]
            {:pre [(s/assert ::mult.fmt.spec/ns-symbol ns-symbol)]}
            (go))
          (eval*
           [_ {:as opts
               :keys [::mult.spec/code-string
                      ::mult.fmt.spec/ns-symbol]}]
           {:pre [(s/assert ::mult.spec/logical-repl-eval-opts opts)]}
           (go
             (when-not (get @stateA ::mult.nrepl.spec/session)
               (<! (init-fn)))
             (<! (back-to-clj-fn))
             (<! (select-build-fn))
             (let [code-string-formatted
                   (format
                    "(binding [*ns* (find-ns '%s)]
                      %s
                      )"
                    ns-symbol code-string)]
               (<! (eval-fn code-string-formatted)))))
          mult.protocols/Release
          (release*
            [_]
            (close! recv|)
            (close! send|))
          #?(:clj clojure.lang.IDeref)
          #?(:clj (deref [_] @stateA))
          #?(:cljs cljs.core/IDeref)
          #?(:cljs (-deref [_] @stateA)))]
    (reset! stateA (merge
                    opts
                    {::opts opts
                     ::mult.nrepl.spec/session nil
                     ::recv| recv|
                     ::recv|mult (mult recv|)
                     ::send| send|}))
    logical-repl))