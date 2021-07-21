(ns placement-explorer-frontend.core
  (:require
   [reagent.core :as reagent :refer [atom, as-element]]
   [reagent.dom :as rdom]
   [reagent.session :as session]
   [reitit.frontend :as reitit]
   [clerk.core :as clerk]
   [accountant.core :as accountant]
   [datascript.core :as d]
   [cljs.reader :as reader]
   [datomic-query-helpers.core :refer [normalize]]
   [cljsjs.echarts]
   [react :as react]))


(defn pp [obj]
  (with-out-str (cljs.pprint/pprint obj)))

(defn ECharts [options]
  (as-element
   (let [mychart (react/useRef nil)]
     (react/useEffect (fn []
                        (set! (.-chart js/document)
                              (.init js/echarts (.-current mychart) (.-theme options)))
                        (.setOption (.-chart js/document) (.-option options)))
                      (clj->js [options js/ResizeObserver]))
     [:div {:ref mychart
            :style (.-style options)}])))

(defn myechart []
  [:> ECharts
   {:style {:width "800px" :height "600px"}
    :theme "dark"
    :option
    {:title {:text "Echarts is here"}
     :dataset {:dimention [:Week :Value]
               :source [{:Week "Mon" :Value 820} {:Week "Tue" :Value 932} {:Week "Wed" :Value 901}
                        {:Week "Thu" :Value 934} {:Week "Fri" :Value 1220} {:Week "Sat" :Value 820}
                        {:Week "Sun" :Value 990}]}
     :xAxis {:type "category"}
     :yAxis {:type "value"}
     :series [{:type "line"
               :smooth true}]}}])

(defn treemap []
    [:> ECharts
   {:style {:width "800px" :height "600px"}
    :theme "dark"
    :option
    {:title {:text "dev01"}
     :series [{:type "treemap"
               :data [{:name "memory"
                       :value 7976
                       :children [{:name "memory_used"
                                   :value 768},
                                  {:name ""
                                   :value 7208},
                                  ]},
                      {:name "disk"
                       :value 4000
                       :children [{:name "disk_used"
                                   :value 2000},
                                  {:name ""
                                   :value 2000},]}
                      {:name "cpu"
                       :value 4000
                       :children [{:name "cpu_used"
                                   :value 2000},
                                  {:name ""
                                   :value 2000}]}]}]}}])

(def query (reagent/atom (str (pp '[:find
                                    ?node
                                    ?cpu_total
                                    ?cpu_used
                                    ?memory
                                    ?memory_used
                                    ?disk
                                    ?disk_used
                                    :where
                                    [?e :node/name ?node]
                                    [?e :memory_mb/total ?memory]
                                    [?e :memory_mb/used ?memory_used]
                                    [?e :disk_gb/total ?disk]
                                    [?e :disk_gb/used ?disk_used]
                                    [?e :vcpu/total ?cpu_total]
                                    [?e :vcpu/used ?cpu_used]]
                                  ))))

;; -------------------------
;; Routes

(def router
  (reitit/router
   [["/" :index]
    ["/items"
     ["" :items]
     ["/:item-id" :item]]
    ["/about" :about]]))

(defn path-for [route & [params]]
  (if params
    (:path (reitit/match-by-name router route params))
    (:path (reitit/match-by-name router route))))

(defn get-data []
  (def conn (d/create-conn))
  (def datoms [{:db/id -1
                :node/name "dev01"
                :memory_mb/total 7976
                :memory_mb/used 768
                :disk_gb/total 4
                :disk_gb/used 2
                :vcpu/total 4
                :vcpu/used 2
                :cloud/name "devstack"}
               {:db/id -2
                :node/name "dev02"
                :memory_mb/total 7976
                :memory_mb/used 768
                :disk_gb/total 4
                :disk_gb/used 2
                :vcpu/total 4
                :vcpu/used 2
                :cloud/name "devstack"}
               ])
  (d/transact! conn datoms)
  (try
    (def q (reader/read-string @query))
    {:columns (:find (normalize q))
     :results (d/q q @conn)}
    (catch :default e e)))

(defn table [results]
  [:table {:border 1}
   [:thead
    [:tr
     (for [column (:columns results)]
       [:th column])]]
   [:tbody
    (for [row (:results results)]
      [:tr
       (for [col row]
         [:td col])])]])

(defn show-graph []
  [#'treemap])

;; -------------------------
;; Page components

(defn home-page []
  (fn []
    [:span.main
     [:h1 "Welcome to Placement-Explorer"]
     [:h2 "Query:"]
     [:textarea {:style {:width 600 :height 300} :on-change #(reset! query (-> % .-target .-value))} @query]
     [:h2 "Results:"]
     (let [results (get-data)]
       (if (contains? results :results)
         [:div {:id "results-container"}
          [:h3 "Table"]
          [:div {:id "table-container"} (table results)]
          [:h3 "Graph"]
          [:div {:id "graph-container"}
           (show-graph)]]
         [:div
          [:h3 "Error"]
          [:pre (pp results)]]))
     ]
    )
  )


(defn items-page []
  (fn []
    [:span.main
     [:h1 "The items of Placement-Explorer"]
     [:ul (map (fn [item-id]
                 [:li {:name (str "item-" item-id) :key (str "item-" item-id)}
                  [:a {:href (path-for :item {:item-id item-id})} "Item: " item-id]])
               (range 1 60))]]))


(defn item-page []
  (fn []
    (let [routing-data (session/get :route)
          item (get-in routing-data [:route-params :item-id])]
      [:span.main
       [:h1 (str "Item " item " of Placement-Explorer")]
       [:p [:a {:href (path-for :items)} "Back to the list of items"]]])))


(defn about-page []
  (fn [] [:span.main
          [:h1 "About Placement-Explorer"]]))


;; -------------------------
;; Translate routes -> page components

(defn page-for [route]
  (case route
    :index #'home-page
    :about #'about-page
    :items #'items-page
    :item #'item-page))


;; -------------------------
;; Page mounting component

(defn current-page []
  (fn []
    (let [page (:current-page (session/get :route))]
      [:div
       [:header
        [:p [:a {:href (path-for :index)} "Home"] " | "
         [:a {:href (path-for :about)} "About Placement-Explorer"]]]
       [page]
       [:footer
        [:p "Placement-Explorer ©"]]])))

;; -------------------------
;; Initialize app

(defn mount-root []
  (rdom/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (clerk/initialize!)
  (accountant/configure-navigation!
   {:nav-handler
    (fn [path]
      (let [match (reitit/match-by-path router path)
            current-page (:name (:data  match))
            route-params (:path-params match)]
        (reagent/after-render clerk/after-render!)
        (session/put! :route {:current-page (page-for current-page)
                              :route-params route-params})
        (clerk/navigate-page! path)
        ))
    :path-exists?
    (fn [path]
      (boolean (reitit/match-by-path router path)))})
  (accountant/dispatch-current!)
  (mount-root))
