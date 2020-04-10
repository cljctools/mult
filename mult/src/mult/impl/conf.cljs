(ns mult.impl.conf
  (:require
   [goog.string :refer [format]]
   [clojure.string :as string]
   [clojure.pprint :refer [pprint]]
   [cljs.reader :refer [read-string]]
   [clojure.walk :as walk]
   [mult.impl.stub :as stub]))

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

(defn dataize
  [conf]
  (walk/postwalk #(when (not (fn? %)) %)  conf))

(comment

  (re-matches  (re-pattern ".+.clj") "asd.clj")
  
  (dataize stub/mult-edn)
  (fn? (fn []) )
  ;;
  )