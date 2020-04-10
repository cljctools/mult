(ns mult.impl.conf
  (:require
   [goog.string :refer [format]]
   [clojure.string :as string]
   [clojure.pprint :refer [pprint]]
   [cljs.reader :refer [read-string]]))

(defn preprocess
  [conf]
  (let []
    (-> conf
        (update :connections #(->> (map (fn [[k v]]
                                          [k (merge v {:id k})])  %)
                                   (into {}))))))

(defn conn-iden
  [conf id]
  (get-in conf [:connections id :iden]))

(defn repl-iden
  [conf k]
  (get-in conf [:repls k :iden]))


(comment
  
  (def f (eval ))
  
  
  ;;
  )