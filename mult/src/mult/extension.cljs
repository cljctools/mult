(ns mult.extension
  (:require
   [clojure.core.async :as a :refer [<! >!  chan go alt! take! put! offer! poll! alts! pub sub
                                     timeout close! to-chan go-loop sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [goog.string :refer [format]]
   [clojure.string :as string]
   ["fs" :as fs]
   ["path" :as path]
   ["net" :as net]
   ["bencode" :as bencode]
   [cljs.reader :refer [read-string]]
   [bencode-cljc.core :refer [serialize deserialize]]
   [mult.protocols :refer [Proc]]
   [mult.impl :refer [procs-impl]]))



; for repl only
(declare  context )

(defn proc-main
  [{:keys [system|pub]} vscode context]
  (def context context)
  (let [sys| (chan 1)]
    (sub system|pub :system sys|)
    (sub system|pub :proc-main sys|)
    (go (loop []
          (if-let [[v port] (alts! [sys|])]
            (condp = port
              sys| (let [{:keys [proc/op]} v]
                     (println (format "; proc-main proc/op %s" op))
                     (condp = op
                       :exit (do nil)
                       :init (let []
                               (proc-ops (select-keys channels [:cmd|  :ops| :system|pub]) vscode context)
                               (recur)))))))
        (println "proc-main exiting"))))

(defn show-information-message
  [vscode msg]
  (.. vscode.window (showInformationMessage msg)))

(defn register-command
  [vscode context out| {:keys [cmd/id]}]
  (let [disposable (.. vscode.commands
                       (registerCommand
                        id
                        (fn [& args]
                          (put! out| {:cmd/id id
                                      :cmd/args args}))))]
    (.. context.subscriptions (push disposable))))

(defn register-default-commands
  [vscode context out|]
  (let [ids ["mult.activate"
             "mult.ping"
             "mult.deactivate"
             "mult.counter"]]
    (doseq [id ids]
      (register-command vscode context out| {:cmd/id id}))))

;repl only
(declare  panel)

(defn tabapp-html
  [vscode context panel]
  (def panel panel)
  (let [script-uri (as-> nil o
                     (.join path context.extensionPath "resources/out/main.js")
                     (vscode.Uri.file o)
                     (.asWebviewUri panel.webview o)
                     (.toString o))
        html (as-> nil o
               (.join path context.extensionPath "resources/index.html")
               (.readFileSync fs o)
               (.toString o)
               (string/replace o "/out/main.js" script-uri))]
    html))

(defn make-tab
  [vscode context id ops|]
  (let [panel (vscode.window.createWebviewPanel
               id
               "mult tab"
               vscode.ViewColumn.Two
               #js {:enableScripts true
                    :retainContextWhenHidden true})
        html (tabapp-html vscode context panel)]
    (.onDidDispose panel (fn []
                           (put! ops| {:op :tab/on-dispose :tab/id id})))
    (.onDidReceiveMessage  panel.webview (fn [data]
                                           (let [msg (read-string data)]
                                             (put! ops| {:op :tab/on-message :tab/msg msg}))))
    (set! panel.webview.html html)
    panel))


(defn proc-ops
  [{:keys [cmd| ops| system|pub]} vscode context]
  (let [sys| (chan 1)]
    (sub system|pub :system sys|)
    (go (loop []
          (if-let [[v port] (alts! [cmd| ops| sys|])]
            (condp = port
              sys| (condp = (:proc/op v)
                     :exit (do nil))
              cmd| (let [{:keys [cmd/id cmd/args]} v]
                     (println (format "; cmd/id %s" id))
                     (condp = id
                       "mult.activate" (do
                                         (show-information-message vscode "mult.activate")
                                         (>! ops| {:op :tab/add}))
                       "mult.ping" (do
                                     (show-information-message vscode "mult.ping via channels")
                                     (println "in 3sec will show another msg")
                                     (<! (timeout 3000))
                                     (show-information-message vscode "mult.ping via channels later"))
                       "mult.counter" (do
                                        (put! ops| {:op :tab/send
                                                    :tab/id :current
                                                    :tab/msg {:op :tabapp/inc}})))
                     (recur))
              ops| (let [{:keys [op]} v]
                     (println (format "; proc-ops %s" op))
                     (condp = op
                       :activate (let []
                                   (register-default-commands  vscode context cmd|))
                       :deactivate (let []
                                     (put! (channels :system|) {:ch/topic :system :proc/op :exit}))
                       :tab/add (let [id (random-uuid)
                                      tab (make-tab vscode context id ops|)]
                                  (swap! state update-in [:tabs] assoc id tab)
                                  (swap! state update-in [:tabs] assoc :current tab))
                       :tab/on-dispose (let [{:keys [tab/id]} v]
                                         (swap! state update-in [:tabs] dissoc id))
                       :tab/on-message (let [{:keys [tab/msg]} v]
                                         (println msg))
                       :tab/send (let [{:keys [tab/id tab/msg]} v
                                       tab (get-in @state [:tabs id])]
                                   (.postMessage (.-webview tab) (str msg))))
                     (recur)))))
        (println "proc-ops exiting"))))

(defn hello-fn []
  (.. vscode.window (showInformationMessage
                     (format "Hello World!!! %s" (type (chan 1)))
                     #_(str "Hello World!" (type (chan 1)))))
  #_(go
      (<! (timeout 3000))
      (put! (channels :ch/test|) {:op :show-info-msg})))

(comment

  (js/console.log 3)
  (js/console.log format)
  (go
    (<! (timeout 2000))
    (js/console.log (type format))
    (js/console.log (format "Hello World! %s" 123))

    (<! (timeout 1000))
    (js/console.log "done"))
  (hello-fn)
  ;;
  )

(comment

  (offer! (channels :ch/editor-ops|)  {:op :show-information-message
                                     :inforamtion-message "message via repl via channel"})

  ;;
  )

(comment

  (def data$ (atom nil))

  (defn on-data
    [buff]
    (println "; net/Socket data")
    (let [benstr (.toString buff)
          o (deserialize benstr)]
      (when (contains? o "value")
        (println o)
        (reset! data$ o))))

  (def ws (let [ws (net/Socket.)]
            (.connect ws #js {:port 5533 #_5511
                              :editor "localeditor"})
            (doto ws
              (.on "connect" (fn []
                               (println "; net/Socket connect")))
              (.on "ready" (fn []
                             (println "; net/Socket ready")))
              (.on "timeout" (fn []
                               (println "; net/Socket timeout")))
              (.on "close" (fn [hadError]
                             (println "; net/Socket close")
                             (println (format "hadError %s"  hadError))))
              (.on "data" (fn [buff] (on-data buff)))
              (.on "error" (fn [e]
                             (println "; net/Socket error")
                             (println e))))
            ws))

  (.write ws (str {:op "eval" :code "(+ 2 3)"}))
  (.write ws (str "error"))
  (dotimes [i 2]
    (.write ws (str {:op "eval" :code "(+ 2 3)"})))
  (dotimes [i 2]
    (.write ws (str "error")))


  (bencode/encode (str {:op "eval" :code "(+ 2 3)"}))
  (bencode/decode (bencode/encode (str {:op "eval" :code "(+ 2 3)"})))

  (.write ws (bencode/encode (str {:op "eval" :code "(+ 2 3)"})))


  (deserialize (serialize {:op "eval" :code "(+ 2 3)"}))

  ; clj only
  (binding [*ns* mult.vscode]
    [3 (type hello-fn)])

  (.write ws (serialize {:op "eval" :code "(+ 2 4)"}))

  (.write ws (serialize {:op "eval" :code "(do
                                           (in-ns 'abc.core)
                                           [(type foo) (foo)]
                                           )"}))



  ;;
  )