(ns cljctools.mult.ui.main
  (:require
   [clojure.core.async :as a :refer [chan go go-loop <! >!  take! put! offer! poll! alt! alts! close!
                                     pub sub unsub mult tap untap mix admix unmix pipe
                                     timeout to-chan  sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [cljs.core.async.impl.protocols :refer [closed?]]
   [cljs.core.async.interop :refer-macros [<p!]]
   [goog.string.format :as format]
   [goog.string :refer [format]]
   [goog.object]
   [clojure.string :as str]
   [cljs.reader :refer [read-string]]

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

   ["antd/lib/checkbox" :default AntCheckbox]


   ["antd/lib/divider" :default AntDivider]
   ["@ant-design/icons/SmileOutlined" :default AntIconSmileOutlined]
   ["@ant-design/icons/LoadingOutlined" :default AntIconLoadingOutlined]
   ["@ant-design/icons/SyncOutlined" :default AntIconSyncOutlined]
   ["@ant-design/icons/ReloadOutlined" :default AntIconReloadOutlined]

   [cljctools.csp.op.spec :as op.spec]
   [cljctools.cljc.core :as cljc.core]

   [cljctools.mult.ui.spec :as ui.spec]
   [cljctools.mult.ui.chan :as ui.chan]))

(goog-define BAR_PORT 0)
(goog-define FOO_ORIGIN "")

#_(set! BAR_PORT (str (subs js/location.port 0 2) (subs (str BAR_PORT) 2)))
#_(set! FOO_ORIGIN "http://localhost:3001")

(def channels (merge
               (ui.chan/create-channels)))

(def state* (reagent.core/atom {}))

(defonce match (r/atom nil))


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

(defonce match (r/atom nil))

(defn current-page []
  [:div
   [:ul
    [:li [:a {:href (rfe/href ::frontpage)} "Frontpage"]]
    [:li
     [:a {:href (rfe/href ::item-list)} "Item list"]]]
   (if @match
     (let [view (:view (:data @match))]
       [view @match]))
   [:pre (with-out-str (fipp.edn/pprint @match))]])

(defn log-fn [& params]
  (fn [_]
    (apply js/console.log params)))

(def routes
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


#_(defn create-proc-ops
    [channels ctx opts]
    (let [{:keys [::ui.chan/ops|]} channels
          {:keys [::ui.spec/state*]} ctx]
      (go
        (loop []
          (when-let [[value port] (alts! [ops|])]
            (condp = port
              ops|
              (condp = (select-keys value [::op.spec/op-key ::op.spec/op-type ::op.spec/op-orient])

                {::op.spec/op-key ::ui.chan/init
                 ::op.spec/op-type ::op.spec/fire-and-forget}
                (let [{:keys []} value]
                  (println ::init)
                  (ui.render/render-ui channels state* {}))

                {::op.spec/op-key ::ui.chan/update-state
                 ::op.spec/op-type ::op.spec/fire-and-forget}
                (let [{:keys []} value]
                  (swap! state* merge value))))

            (recur))))))

(defn ^:export main
  []
  (println ::main)
  (println ::BAR_PORT BAR_PORT)
  #_(create-proc-ops channels ctx {})
  (rfe/start!
   routes
   (fn [new-match]
     (swap! match (fn [old-match]
                    (if new-match
                      (assoc new-match :controllers (rfc/apply-controllers (:controllers old-match) new-match))))))
   {:use-fragment true})
  (reagent.dom/render [current-page] (.getElementById js/document "ui"))
  #_(reagent.dom/render [:f> rc-current-page channels state*]  (.getElementById js/document "ui"))
  #_(ui.chan/op
     {::op.spec/op-key ::ui.chan/init
      ::op.spec/op-type ::op.spec/fire-and-forget}
     channels
     {}))

(do (main))
