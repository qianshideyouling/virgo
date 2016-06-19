(ns virgo.httpkit.proxy-test
  ;does not work for now
  (:use [org.httpkit.server :only [run-server async-response]]
        [org.httpkit.client :only [request]]))
  
(defn- proxy-opts [req]
  {:url (str "http://localhost:8080" (:uri req)
             (if-let [q (:query-string req)]
               (str "?" q)
               ""))
   :timeout 30000 ;ms
   :method (:request-method req)
   :headers (assoc (:headers req)
              "X-Forwarded-For" (:remote-addr req))
   :body (:body req)})
  
(defn handler [req]
  (async-response respond
                  (request (proxy-opts req)
                           (fn [{:keys [status headers body error]}]
                             (if error
                               (respond {:status 503
                                         :headers {"Content-Type" "text/plain"}
                                         :body (str "Cannot access backend\n" error)})
                               (respond {:status status
                                         :headers (zipmap (map name (keys headers)) (vals headers))
                                         :body body}))))))

(defn -main [& args]
  (run-server handler {:port 8080})
  (println "proxy server started at 0.0.0.0@8080"))
