(ns mult.impl.stub)

; this file is for repl use only
; has to be this way due to certain issues with shadow-cljs and (eval)
; actual compilation of extension will use deafult cljs cli, with which eval works
; then actual .edn files will be used, with code as data 

(def mult-edn
  {:connections {["localhost" 7788] {:name :server
                                     :iden {:type :nrepl}}
                 ["localhost" 8899] {:name :ui
                                     :iden {:type :nrepl}}
                 ["localhost" 5100] {:name :mult
                                     :iden {:type :nrepl}}}
   :repls {:ui {:iden {:type :shadow-cljs
                       :runtime :cljs
                       :build :app}
                :conn :ui
                :include (fn [file-uri]
                           (re-matches #".+.cljs" file-uri))}
           :test {:iden {:type :shadow-cljs
                         :runtime :cljs
                         :build :test}
                  :include (fn [file-uri]
                             (re-matches #".+.cljs" file-uri))
                  :conn :ui}
           :devserver  {:iden {:type :shadow-cljs
                               :runtime :clj}
                        :include (fn [file-uri]
                                   (re-matches #".+.cljs" file-uri))
                        :conn :ui}
           :server {:iden {:type :nrepl-server
                           :runtime :clj}
                    :include (fn [file-uri]
                               (re-matches #".+.cljs" file-uri))
                    :conn :server}
           :mult {:iden {:type :shadow-cljs
                         :runtime :cljs
                         :build :extension}
                  :include (fn [file-uri]
                             (re-matches #".+.cljs" file-uri))
                  :conn :mult}}
   :tabs {:system {:repls {:ui {}
                           :devserver {}
                           :server {}}
                   :cljc-prefer :recent}
          :mult {:repls {:mult {}}
                 :cljc-prefer :cljs}}
   :tabs/default #{:system}})