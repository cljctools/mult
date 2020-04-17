(ns mult.impl.lrepl
  (:require
   [clojure.core.async :as a :refer [<! >!  chan go alt! take! put! offer! poll! alts! pub sub unsub
                                     timeout close! to-chan  mult tap untap mix admix unmix
                                     pipeline pipeline-async go-loop sliding-buffer dropping-buffer]]
   [goog.string :refer [format]]
   [clojure.string :as string]
   [cljs.reader :refer [read-string]]
   [mult.protocols :as p]
   [mult.impl.channels :as channels]))


(defn lrepl-plain
  []
  (let []
    (reify
      p/Eval
      (-eval [_ conn code session-id ns-sym]
             (let [code' (format
                          "(do (in-ns '%s) %s)" ns-sym code)]
               (p/-eval conn {:code code'
                              :session-id session-id}))))))

(comment
  
  (let [ns-sym 'foo.bar]
    `(in-ns ~ns-sym b c))
  ;;
  )

(defn lrepl-shadow-clj
  [{:keys [build]}]
  (let []
    (reify
      p/Eval
      (-eval [_ conn code ns-sym]
        (p/-eval conn {:code code})))))

(defn lrepl-shadow-cljs
  [{:keys [build]}]
  (let []
    (reify
      p/Eval
     (-eval [_ conn code ns-sym]
            (let [code0 (format "(shadow.cljs.devtools.api/nrepl-select %s)" build)
                  code' (format
                         "(do (in-ns '%s) %s)" build ns-sym code)]
              (go
                #_(prn (<! (p/-eval conn code0 session-id)))
                (<! (p/-eval conn {:code code
                                   :done-keys [:err :value]}))))) 
      )))

(defn lrepl
  [{:keys [type runtime build] :as opts}]
  (cond
    (= [type] [:nrepl-server]) (lrepl-plain)
    (= [type runtime] [:shadow-cljs :cljs]) (lrepl-shadow-cljs opts)
    (= [type runtime] [:shadow-cljs :clj]) (lrepl-shadow-clj opts)
    :else (throw (ex-info "No lrepl for options " opts))))


(comment
  

  ;;
  )