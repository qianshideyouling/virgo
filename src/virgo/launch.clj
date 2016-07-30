(ns virgo.launch
  (:require [virgo.core :as core]
            [virgo.helper :as helper]))

(defonce start-pairs
  [["post" "/server/server/10002"]
   ["post" "/static/add/web/web"]])

(defn -main []
  (print "------------------ virgo start  --------------------\r\n")
    (helper/command-line-balancer start-pairs)
  (print "------------------virgo complete--------------------"))

