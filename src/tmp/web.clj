(ns tmp.web
  (:use org.httpkit.server))

(defn app [req]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (str "hello ithomer.net" req)})

;(run-server app { :port 8888 :thread 10})

(defonce server (atom nil))

(defn stop-server []
  (when-not (nil? @server)
    ;; graceful shutdown: wait 100ms for existing requests to be finished
    ;; :timeout is optional, when no timeout, stop immediately
    (@server :timeout 100)
    (reset! server nil)))

(defn -main [&args]
  ;; The #' is useful when you want to hot-reload code
  ;; You may want to take a look: https://github.com/clojure/tools.namespace
  ;; and http://http-kit.org/migration.html#reload
  (reset! server (run-server #'app {:port 8080})))