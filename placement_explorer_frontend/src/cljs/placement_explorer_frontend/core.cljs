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
   [cljs.pprint]
   [react :as react]
   [cljs.core.async :refer [<!, chan]]
   [cljs-http.client :as http]
   [clojure.contrib.humanize :as human]
   )
  (:require-macros
   [cljs.core.async.macros :refer [go]])
  )


;; --- config

(def DATA-URL "//localhost:5000/resource")
(def USE-FAKE-DB true)
(def BLOCK-SIZE-BASE 200)
(def BLOCK-SIZE-VARIABLE 100)
(def DEBUG true)

;; --- utils

(defn debug [& objs]
  (if DEBUG
    (prn (clojure.string/join " " objs))))

(defn pp [obj]
  (with-out-str (cljs.pprint/pprint obj)))

(defn ECharts [options]
  (as-element
   (let [mychart (react/useRef nil)]
     (react/useEffect (fn []
                        (set! (.-chart js/document)
                              (.init js/echarts (.-current mychart) (.-theme options)))
                        (try
                          (.setOption (.-chart js/document) (.-option options))
                          (catch :default e
                            (.error js/console (str "Graph construction error " e)))))
                      (clj->js [options js/ResizeObserver]))
     [:div {:ref mychart
            :style (.-style options)}])))

;; --- treemap

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
          (assoc item :children (conj (:children item) {:name (str (:name item) "-free") :value (- (:value item) s)})))))))

(defn treemap-title [row columns]
  (let [sorted (for [[i v] (map-indexed vector columns)] [v (nth row i)])
        ]
    (second (first (filter (fn [[k v]] (string? v)) sorted))))
  )

(defn treemap-data [row]
  (let [top-level (filter (fn [[k v]] (and (number? v) (not (clojure.string/includes? k "-")))) row)
        ]
    (treemap-add-available (build-children (for [[k v] top-level] {:name k :value v}) row 1))
    )
  )

(defn treemap [title data size]
  [:> ECharts
   {:style {:width size :height size}
    :theme "dark"
    :option
    {:title {:text title}
     :tooltip {:formatter (fn [info]
                            (str "name: "
                                 (clojure.string/join "/" (map #(.-name %) (.-treePathInfo info)))
                                 "<br/>value: "    (human/filesize (.-value info) :binary false)))}
     :series [{:type "treemap"
               :data data}]}}])

(defn update-map [[i m] merge-value]
  (reduce-kv (fn [m k v]
               (assoc m
                      (let [replacement (if merge-value merge-value (str (+ i 1)))]
                        (if (clojure.string/includes? k "|")
                          (clojure.string/replace k #"\|" replacement) k)) v)) {} m))

(defn merge-rows [rows merge-index]
  (debug "merge-index" merge-index)
  (reduce
   merge
   (map (fn [indexed-row] (update-map indexed-row (get (second indexed-row) merge-index))) (map-indexed vector rows)))
  )

(defn merge-results [results]
  (let [title-index (filter #(string? (second %)) (map-indexed vector (first (:rows results))))
        rows (for [i (:rows results)] (zipmap (:columns results) i))
        i (first (second title-index))
        merge-index (if i (nth (:columns results) i))
        ]
    (if (not (empty? title-index))
      (map
       (fn [[k v]] [k (merge-rows v merge-index)])
       (group-by #(get % (nth (:columns results) (ffirst title-index))) rows))
      )
    )
  )

;; --- database

(def schema {:node/name {:db/unique :db.unique/identity}})

(defonce datoms (reagent/atom []))

(def fake-datoms [{:db/id -1
                   :node/name "cmp001"
                   :node/memory 7976
                   :node/memory_used 768
                   :node/disk 4096
                   :node/disk_used 2048
                   :node/cpu 4
                   :node/cpu_used 2
                   :cloud/name "devstack"}
                  {:db/id -2
                   :node/name "cmp002"
                   :node/memory 7976
                   :node/memory_used 768
                   :node/disk 4096
                   :node/disk_used 2048
                   :node/cpu 4
                   :node/cpu_used 2
                   :cloud/name "devstack"}
                  {:db/id -3
                   :node/name "cmp003"
                   :node/memory 7976
                   :node/memory_used 768
                   :node/disk 4096
                   :node/disk_used 2048
                   :node/cpu 4
                   :node/cpu_used 2
                   :cloud/name "devstack"}
                  {:db/id -100
                   :instance/name "vm1"
                   :instance/memory 384
                   :instance/disk 1024
                   :instance/cpu 1
                   :instance/host [:node/name "cmp001"]}
                  {:db/id -101
                   :instance/name "vm2"
                   :instance/memory 384
                   :instance/disk 1024
                   :instance/cpu 1
                   :instance/host [:node/name "cmp001"]}
                  {:db/id -102
                   :instance/name "vm3"
                   :instance/memory 768
                   :instance/disk 2048
                   :instance/cpu 2
                   :instance/host [:node/name "cmp002"]}
                  {:db/id -103
                   :instance/name "vm4"
                   :instance/memory 768
                   :instance/disk 2048
                   :instance/cpu 2
                   :instance/host [:node/name "cmp003"]}
                  ])

(def query (reagent/atom (str (pp '[:find
                                    ?cloud
                                    (sum ?mem)
                                    (sum ?disk)
                                    (sum ?cpu)
                                    :with
                                    ?node
                                    :where
                                    [?n :cloud/name ?cloud]
                                    [?n :node/memory ?mem]
                                    [?n :node/disk ?disk]
                                    [?n :node/cpu ?cpu]
                                    [?n :node/name ?node]
                                    ]
                                  ))))

(defn query-data []
  (let [conn (d/create-conn schema)]
    (d/transact! conn @datoms)
    (try
      (def q (reader/read-string @query))
      {:columns (map (fn [x] (if (symbol? x) (subs (name x) 1) (str x))) (:find (normalize q)))
       :rows (d/q q @conn)}
      (catch :default e e))))

(defn resource-mapping [k v]
  (let [v (if (contains? #{:disk :memory} k) (* 1024 1024 v) v)]
    [k v]))

(defn set-resource [m k v]
  (let [[k v-total] (resource-mapping k (:total v))
        [k v-used] (resource-mapping k (:used v))]
   (assoc (assoc m (keyword (str "node/" (name k))) v-total)
          (keyword (str "node/" (name k) "_used")) v-used))
  )

(defn set-instance-resource [m k v]
  (let [[k v] (resource-mapping k v)]
   (assoc m (keyword (str "instance/" (name k))) v))
  )

(defn import-server-data []
  (let [out (chan)]
    (go (let [response (<! (http/get DATA-URL {:with-credentials? false :keywordize-keys? false}))
              nodes (apply concat (for [[cloud-name cloud] (:body response)]
                                    (for [[node-name node] (take 10 (:nodes cloud))]
                                      (merge
                                       {:cloud/name (name cloud-name)
                                        :node/name (name node-name)
                                        :db/id (name node-name)}
                                       (reduce-kv set-resource {} (:resources node))
                                       )
                                      )
                                    ))
              instances (apply concat (apply concat (for [[cloud-name cloud] (:body response)]
                                                      (for [[node-name node] (take 10 (:nodes cloud))]
                                                        (for [[instance-uuid resources] (:instances node)]
                                                          (merge
                                                           {:db/id (name instance-uuid)
                                                            :instance/name (name instance-uuid)
                                                            :instance/host [:node/name (name node-name)]
                                                            }
                                                           (reduce-kv set-instance-resource {} resources)
                                                           ))
                                                        ))))
              ]
          (let [loaded-datoms (concat nodes instances)]
            (debug "Got data from server" loaded-datoms)
            (reset! datoms loaded-datoms))
          )
        ))
  )

;; -------------------------
;; --- Routes

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

;; -------------------------
;; --- Page components

(defn table [results]
  [:table {:border 1}
   [:thead
    [:tr
     (for [column (:columns results)]
       [:th column])]]
   [:tbody
    (for [row (:rows results)]
      [:tr
       (for [col row]
         [:td col])])]])

(defn show-graph [results]
  [:div
   ;; TODO: calculate row sum for top level only
   (let [row-sums (map (fn [x] (reduce + (filter (fn [y] (number? y)) x))) (:rows results))
         max-sum (reduce max row-sums)
         max-size (+ BLOCK-SIZE-BASE BLOCK-SIZE-VARIABLE)
         ]
     (for [[name row] (merge-results results)]
       [:div {:style {:float "left" :height max-size :width max-size}}
        (let [K (/ (reduce + (filter (fn [y] (number? y)) (vals row))) max-sum)
              ]
          (debug "treemap" name (treemap-data row) (+ BLOCK-SIZE-BASE (* BLOCK-SIZE-VARIABLE K)))
          [(fn [] (treemap
                   name
                   (treemap-data row)
                   (+ BLOCK-SIZE-BASE (* BLOCK-SIZE-VARIABLE K))))])]
       )
     )
   ]
  )

(defn home-page []
  (fn []
    [:span.main
     [:h1 "Placement-Explorer"]
     [:h2 "Query:"]
     [:textarea {:style {:width 600 :height 300} :on-change #(reset! query (-> % .-target .-value))} @query]
     [:h2 "Results:"]
     (let [results (query-data)]
       (if (contains? results :rows)
         [:div {:id "results-container"}
          [:h3 "Table"]
          [:div {:id "table-container"} (table results)]
          [:h3 "Graph"]
          [:div {:id "graph-container"}
           (try
             (show-graph results)
             (catch :default e [:h4 (str "Graph error" e)]))]]
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
          [:h1 "Docs"]
          [:ul
           [:li [:a {:href "https://github.com/amadev/placement-explorer"} "Placement-Explorer schema and examples"]]
           [:li [:a {:href "https://docs.datomic.com/on-prem/query/query.html#queries"} "Datalog queries"]]
           [:li [:a {:href "http://www.learndatalogtoday.org"} "Learn Datalog Today"]]
           ]]))


;; -------------------------
;; --- Translate routes -> page components

(defn page-for [route]
  (case route
    :index #'home-page
    :about #'about-page
    :items #'items-page
    :item #'item-page))


;; -------------------------
;; --- Page mounting component

(defn current-page []
  (fn []
    (let [page (:current-page (session/get :route))]
      [:div
       [:header
        [:p [:a {:href (path-for :index)} "Home"] " | "
         [:a {:href (path-for :about)} "Docs"]]]
       [page]
       [:footer {:style {:clear "both"}}
        [:p "Placement-Explorer ©"]]])))

;; -------------------------
;; --- Initialize app

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
  (if USE-FAKE-DB
    (reset! datoms fake-datoms)
    (import-server-data))
  (mount-root))
