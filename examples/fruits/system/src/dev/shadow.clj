(ns dev.shadow
  (:require
   [clojure.repl :refer [dir doc]]
   [shadow.cljs.devtools.api :as shadow]))

(defn abc [])

(comment

  ; api
  (dir shadow)

  (+ 1 2)

  (shadow/compile :app)


  ;;
  )