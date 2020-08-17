(ns mult.gui.main
  (:require
   [clojure.core.async :as a :refer [<! >!  chan go alt! take! put! offer! poll! alts! pub sub
                                     timeout close! to-chan go-loop sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [goog.string :refer [format]]
   [cljs.reader :refer [read-string]]

   [cljctools.csp.op.spec :as op.spec]
   [cljctools.vscode.tab-conn.impl :as tab-conn.impl]
   [cljctools.vscode.tab-conn.chan :as tab-conn.chan]

   [mult.gui.render]
   [mult.gui.chan]

   [mult.spec]
   [mult.chan]))


(def channels (merge
               (tab-conn.chan/create-channels)))

(def state (mult.gui.render/create-state {}))

(def tab-conn (tab-conn.impl/create-proc-conn
               (merge channels {::tab-conn.chan/recv|  (::mult.gui.chan/ops| channels)
                                ::tab-conn.chan/recv|m (::mult.gui.chan/ops|m channels)})
               {}))

(defn create-proc-ops
  [channels state]
  (let [{:keys [::mult.gui.chan/ops|m]} channels
        ; recv|t (tap recv|m (chan 10))
        ops|t (tap ops|m (chan 10))]
    (.addEventListener js/document "keyup"
                       (fn [ev]
                         (cond
                           (and (= ev.keyCode 76) ev.ctrlKey) (println ::ctrl+l) #_(swap! state assoc :data []))))
    (go
      (loop []
        (when-let [[v port] (alts! [ops|t])]
          (condp = port
            ops|t
            (condp = (select-keys v [::op.spec/op-key ::op.spec/op-type])

              {::op.spec/op-key ::mult.gui.chan/init}
              (let []
                (mult.gui.render/render-ui channels state {}))

              {::op.spec/op-key ::mult.gui.chan/update-state}
              (let [{state* ::mult.spec/state} v]
                (reset! state state*)))))
        (recur))
      (println (format "go-block exit %s" ::create-proc-ops)))))


(def proc-ops (create-proc-ops channels state))

(defn ^:export main
  []
  (println ::main)
  (mult.gui.chan/op
   {::op.spec/op-key ::mult.gui.chan/init}
   channels))

(do (main))