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

(defn build-children [root data level]
  (if (empty? root)
    root
   (for [item root]
     (let [children (filter
                     (fn [[k v]] (and
                                  (number? v)
                                  (clojure.string/starts-with? k (:name item))
                                  (not= k (:name item))
                                  (= level (count (re-seq #"-" k)))))
                     data)
           new-root (for [[k v] children] {:name k :value v})]
       (assoc item :children (build-children new-root data (+ 1 level)))
       )
     ))
  )

(defn treemap-add-available [root]
  (if (empty? root)
    root
    (for [item root]
      (let [s (reduce + (map (fn [v] (:value v)) (:children item)))]
        (if (not= (:value item) s)
          (assoc item :children (conj (:children item) {:name (str (:name item) "-available") :value (- (:value item) s)})))))))

(defn prepare-treemap-data [row columns]
  (let [columns (map (fn [x] (subs (name x) 1)) columns)
        data (zipmap columns row)
        top-level (filter (fn [[k v]] (and (number? v) (not (clojure.string/includes? k "-")))) data)
        title (second (first (filter (fn [[k v]] (string? v)) data)))]
    [title (treemap-add-available (build-children (for [[k v] top-level] {:name k :value v}) data 1))]
    )
  )

(defn treemap [title data size]
  [:> ECharts
   {:style {:width size :height size}
    :theme "dark"
    :option
    {:title {:text title}
     :series [{:type "treemap"
               :data data}]}}])

(def query (reagent/atom (str (pp '[:find
                                    ?node
                                    ?cpu
                                    ?cpu-used
                                    ?mem
                                    ?mem-used
                                    ?disk
                                    ?disk-used
                                    :where
                                    [?e :node/name ?node]
                                    [?e :memory_mb/total ?mem]
                                    [?e :memory_mb/used ?mem-used]
                                    [?e :disk_gb/total ?d]
                                    [?e :disk_gb/used ?du]
                                    [?e :vcpu/total ?c]
                                    [?e :vcpu/used ?cu]
                                    [(* 1000 ?c) ?cpu]
                                    [(* 1000 ?cu) ?cpu-used]
                                    [(* 1000 ?d) ?disk]
                                    [(* 1000 ?du) ?disk-used]

                                    ]
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
                :node/name "cmp001"
                :memory_mb/total 7976
                :memory_mb/used 768
                :disk_gb/total 4
                :disk_gb/used 2
                :vcpu/total 4
                :vcpu/used 2
                :cloud/name "devstack"}
               {:db/id -2
                :node/name "cmp002"
                :memory_mb/total 7976
                :memory_mb/used 768
                :disk_gb/total 4
                :disk_gb/used 2
                :vcpu/total 4
                :vcpu/used 2
                :cloud/name "devstack"}
               {:db/id -3
                :node/name "cmp003"
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

(defn show-graph [results]
  [:div
   (let [row-sums (map (fn [x] (reduce + (filter (fn [y] (number? y)) x))) (:results results))
         max-sum (reduce max row-sums)
         ]
     (for [row (:results results)]
       [:div {:style {:float "left"}}
        (let [[title data] (prepare-treemap-data row (:columns results))
              K (/ (reduce + (filter (fn [y] (number? y)) row)) max-sum)
              ]
          [(fn [] (treemap title data (+ 200 (* 100 K))))])]
       )
     )
   ]
  )

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
           (show-graph results)]]
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
       [:footer {:style {:clear "both"}}
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
