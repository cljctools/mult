(ns mult.chan
  #?(:cljs (:require-macros [mult.chan]))
  (:require
   [clojure.core.async :as a :refer [chan go go-loop <! >!  take! put! offer! poll! alt! alts! close!
                                     pub sub unsub mult tap untap mix admix unmix pipe
                                     timeout to-chan  sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [clojure.spec.alpha :as s]
   [cljctools.csp.op.spec :as op.spec]
   [mult.spec :as mult.spec]))

(do (clojure.spec.alpha/check-asserts true))

(defmulti ^{:private true} op* op.spec/op-spec-dispatch-fn)
(s/def ::op (s/multi-spec op* op.spec/op-spec-retag-fn))
(defmulti op op.spec/op-dispatch-fn)

(defn create-channels
  []
  (let [ops| (chan 10)
        ops|m (mult ops|)
        ops|x (mix ops|)]
    {::ops| ops|
     ::ops|m ops|m
     ::ops|x ops|x}))

(defmethod op*
  {::op.spec/op-key ::update-settings-filepaths
   ::op.spec/op-type ::op.spec/request} [_]
  (s/keys :req [::op.spec/out|]))

(defmethod op
  {::op.spec/op-key ::update-settings-filepaths
   ::op.spec/op-type ::op.spec/request}
  ([op-meta channels]
   (op op-meta channels (chan 1)))
  ([op-meta channels out|]
   (put! (::ops| channels)
         (merge op-meta
                {::op.spec/out| out|}))
   out|))


(defmethod op*
  {::op.spec/op-key ::update-settings-filepaths
   ::op.spec/op-type ::op.spec/response} [_]
  (s/keys :req []))

(defmethod op
  {::op.spec/op-key ::update-settings-filepaths
   ::op.spec/op-type ::op.spec/response}
  [op-meta out| settings-filepaths]
  (put! out|
        (merge op-meta
               {})))

(defmethod op*
  {::op.spec/op-key ::apply-settings-file
   ::op.spec/op-type ::op.spec/request} [_]
  (s/keys :req []))

(defmethod op
  {::op.spec/op-key ::apply-settings-file
   ::op.spec/op-type ::op.spec/request}
  ([op-meta channels filepath]
   (op op-meta channels filepath (chan 1)))
  ([op-meta channels filepath out|]
   (put! (::ops| channels) (merge op-meta
                                  {::op.spec/out| out|}))
   out|))

(defmethod op*
  {::op.spec/op-key ::apply-settings-file
   ::op.spec/op-type ::op.spec/response} [_]
  (s/keys :req []
          :opt []))

(defmethod op
  {::op.spec/op-key ::apply-settings-file
   ::op.spec/op-type ::op.spec/response}
  [op-meta out|]
  (put! out| op-meta))
