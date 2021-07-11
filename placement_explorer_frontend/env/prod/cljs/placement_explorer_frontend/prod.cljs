(ns placement-explorer-frontend.prod
  (:require [placement-explorer-frontend.core :as core]))

;;ignore println statements in prod
(set! *print-fn* (fn [& _]))

(core/init!)
