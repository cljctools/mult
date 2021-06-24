(ns cljctools.mult.ui.core
  (:require
   [clojure.core.async :as a :refer [chan go go-loop <! >!  take! put! offer! poll! alt! alts! close!
                                     pub sub unsub mult tap untap mix admix unmix pipe
                                     timeout to-chan  sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [clojure.string]
   [clojure.pprint :refer [pprint]]
   [cljs.core.async.impl.protocols :refer [closed?]]
   [cljs.core.async.interop :refer-macros [<p!]]
   [goog.string.format :as format]
   [goog.string :refer [format]]
   [goog.object]
   [cljs.reader :refer [read-string]]
   [clojure.spec.alpha :as s]

   ;; reitit

   [reitit.frontend :as rf]
   [reitit.frontend.easy :as rfe]
   [reitit.coercion.spec :as rss]
   [reitit.frontend.controllers :as rfc]
   [spec-tools.data-spec :as ds]
   ;; Just for pretty printting the match
   [fipp.edn]

   ;; render

   [reagent.core :as r]
   [reagent.dom]
   ["react" :as React :refer [useEffect]]
   ["antd/lib/layout" :default AntLayout]
   ["antd/lib/menu" :default AntMenu]
   ["antd/lib/icon" :default AntIcon]
   ["antd/lib/button" :default AntButton]
   ["antd/lib/list" :default AntList]
   ["antd/lib/row" :default AntRow]
   ["antd/lib/col" :default AntCol]
   ["antd/lib/form" :default AntForm]
   ["antd/lib/input" :default AntInput]
   ["antd/lib/tabs" :default AntTabs]
   ["antd/lib/table" :default AntTable]
   ["antd/lib/tag" :default AntTag]

   ["antd/lib/checkbox" :default AntCheckbox]


   ["antd/lib/divider" :default AntDivider]
   ["@ant-design/icons/SmileOutlined" :default AntIconSmileOutlined]
   ["@ant-design/icons/LoadingOutlined" :default AntIconLoadingOutlined]
   ["@ant-design/icons/SyncOutlined" :default AntIconSyncOutlined]
   ["@ant-design/icons/ReloadOutlined" :default AntIconReloadOutlined]

   [cljctools.mult.spec :as mult.spec]))

(do (clojure.spec.alpha/check-asserts true))

(s/def ::send| ::mult.spec/channel)
(s/def ::recv| ::mult.spec/channel)

(s/def ::create-opts (s/keys :req [::send|
                                   ::recv|]
                             :opt []))

(declare current-page
         routes
         send-data)

(defonce matchA (r/atom nil))
(defonce stateA (r/atom
                    ^{:type ::mult.spec/ui-state}
                    {}))

(defn create
  [{:as opts
    :keys [::recv|
           ::send|]}]
  {:pre [(s/assert ::create-opts opts)]}
  (let [inputs| (chan 10)]
    (rfe/start!
     (routes)
     (fn [new-match]
       (swap! matchA (fn [old-match]
                       (if new-match
                         (assoc new-match :controllers (rfc/apply-controllers (:controllers old-match) new-match))))))
     {:use-fragment false})
    (rfe/push-state ::frontpage)
    (reagent.dom/render [current-page matchA] (.getElementById js/document "ui"))
    (go
      (loop []
        (let [[value port] (alts! [recv|])]
          (when value
            (condp = port

              inputs|
              (condp = (:op value)

                ::foo
                (let []
                  (send-data send| {:op ::mult.spec/op-ping})))

              recv|
              (condp = (:op value)

                ::mult.spec/op-ping
                (let []
                  (println ::ping value))

                ::mult.spec/op-update-ui-state
                (let [{:keys []} value]
                  #_(println ::op-update-ui-state)
                  (swap! stateA merge value))))
            (recur)))))))

(defn send-data
  [send| data]
  {:pre [(s/assert ::mult.spec/op (:op data))]}
  (put! send| (pr-str data)))

(defn home-page []
  [:div
   [:h2 "Welcome to frontend"]
   [:p "Look at console log for controller calls."]])

(defn item-page [match]
  (let [{:keys [path query]} (:parameters match)
        {:keys [id]} path]
    [:div
     [:ul
      [:li [:a {:href (rfe/href ::item {:id 1})} "Item 1"]]
      [:li [:a {:href (rfe/href ::item {:id 2} {:foo "bar"})} "Item 2"]]]
     (if id
       [:h2 "Selected item " id])
     (if (:foo query)
       [:p "Optional foo query param: " (:foo query)])]))

(defn current-page [matchA]
  (r/with-let
    [eval-valueA (r/cursor stateA [::mult.spec/eval-value])
     eval-errA (r/cursor stateA [::mult.spec/eval-err])
     eval-outA (r/cursor stateA [::mult.spec/eval-out])
     configA (r/cursor stateA [::mult.spec/config])
     ns-symbolA (r/cursor stateA [::mult.spec/ns-symbol])
     active-nrepl-idA (r/cursor stateA [::mult.spec/nrepl-id])]
    (let [active-nrepl-id @active-nrepl-idA
          config @configA]
      [:<>
       [:> AntRow]
       [:> AntRow
        [:span
         (map (fn [{:keys [::mult.spec/nrepl-id] :as nrepl-meta}]
                (let [active? (= active-nrepl-id  nrepl-id)]
                  ^{:key nrepl-id}
                  [:span {:style {:cursor "pointer"
                                  :margin-right 8
                                  :color (if active? "black" "grey")}} (str nrepl-id)]))
              (::mult.spec/nrepl-metas config))]]
       [:> AntRow
        [:div @ns-symbolA]]

       (when @eval-outA
         [:> AntRow
          [:pre
           @eval-outA]])

       (when @eval-errA
         [:> AntRow
          [:pre
           @eval-errA]])

       [:> AntRow
        [:pre
         @eval-valueA
         #_(map-indexed (fn [i v]
                          ^{:key i} [:pre {} (with-out-str (pprint v))])
                        @eval-result)]]]))

  #_[:div
     [:ul
      [:li [:a {:href (rfe/href ::frontpage)} "Frontpage"]]
      [:li
       [:a {:href (rfe/href ::item-list)} "Item list"]]]
     (if @matchA
       (let [view (:view (:data @matchA))]
         [view @matchA]))
     [:pre (with-out-str (fipp.edn/pprint @matchA))]])

(defn log-fn [& params]
  (fn [_]
    (apply js/console.log params)))

(defn routes
  []
  (rf/router
   ["/"
    [""
     {:name ::frontpage
      :view home-page
      :controllers [{:start (log-fn "start" "frontpage controller")
                     :stop (log-fn "stop" "frontpage controller")}]}]
    ["items"
      ;; Shared data for sub-routes
     {:view item-page
      :controllers [{:start (log-fn "start" "items controller")
                     :stop (log-fn "stop" "items controller")}]}

     [""
      {:name ::item-list
       :controllers [{:start (log-fn "start" "item-list controller")
                      :stop (log-fn "stop" "item-list controller")}]}]
     ["/:id"
      {:name ::item
       :parameters {:path {:id int?}
                    :query {(ds/opt :foo) keyword?}}
       :controllers [{:parameters {:path [:id]}
                      :start (fn [{:keys [path]}]
                               (js/console.log "start" "item controller" (:id path)))
                      :stop (fn [{:keys [path]}]
                              (js/console.log "stop" "item controller" (:id path)))}]}]]]
   {:data {:controllers [{:start (log-fn "start" "root-controller")
                          :stop (log-fn "stop" "root controller")}]
           :coercion rss/coercion}}))

#_(defn rc-current-page
    [channels state*]
    [rc-layout channels state*
     (if @match
       (let [page (:page (:data @match))]
         [page @match]))])