(ns core.service
  (:require [clojure.java.io :as jio]
            [clojure.string :as str]
            [org.httpkit.client :as client]
            [compojure.route :as route]
            [compojure.core :as comp]
            [org.httpkit.server :as http]
            [clout.core :as clout]
            [clojure.tools.nrepl.server :as nrepl]
            [clojure.xml :as xml]
            [ring.util.response :as response]
            [ring.util.mime-type :as mime]
            [core.helper :as helper]))

(helper/extend-tunnel :post "/server/:name/:port"
                      (fn [{{label :name port :port} :params}]
                        (helper/run-reserve label)
                        (let [kill (http/run-server (fn [request]
                                                      (helper/web-balancer request))
                                                    {:port (Long/parseLong (str port))
                                                     :thread 5
                                                     :max-line (* 100 1024)
                                                     :max-body (- (* 100 1024 1024) 1)})]
                          (do (helper/run-register label kill :norestart)
                          {:status 200 :body (str "Server started, port: " port ", action: " label)}))))

(def statics (atom {}))

(helper/extend-tunnel :post "/static/add/:folder/*"
                      (fn [{{path :*
                             folder :folder} :params}]
                        (let [label (str "static-" folder)
                              _ (helper/run-reserve label)
                              _ (swap! statics assoc folder path)
                              _ (helper/run-register label
                                                     (fn [] (swap! statics dissoc folder))
                                                     (fn [] (comment "nothing to do")))]
                          {:status 200
                           :body (str "added static " folder " -> " path)})))

(helper/extend-tunnel :get "/web/:folder/*"
                      (fn [{{path :*
                             folder :folder} :params}]
                        (let [root (get @statics folder)
                              _ (when-not root (throw (Exception. (str "could not find folder: " folder))))
                              target (.getAbsolutePath (helper/sub-path root path))]
                          (merge (response/file-response target)
                                 {:mime (mime/ext-mime-type target)}))))

;;can not stop repl for now
(helper/extend-tunnel :post "/maintain/:name/:port"
                      (fn [{{label :name port :port} :params}]
                        (helper/run-reserve label)
                        (let [kill (helper/start-repl port)]
                          (do (helper/run-register label
                                                   (fn [] (helper/stop-repl))
                                                   :norestart)
                          {:status 200 :body (str "maintain repl started, port: " port ", action: " label)}))))

(helper/extend-tunnel :get "/runners/ls"
                      (fn [& x] {:status 200 :body (str "running task:\r\n"
                                                                      (keys  @helper/runners))}))

(helper/extend-tunnel :post "/runners/stop/:name"
                      (fn [{{label :name} :params}]
                        (let [kill (helper/run-cancel label)]
                          {:status 200 :body (str "has been stopped, action: " label)})))


(helper/extend-tunnel :get "/hello" (fn [& x] {:status 200 :body (str "hello world----\r\n"
                                                                      (with-out-str (clojure.pprint/pprint x)))}))
