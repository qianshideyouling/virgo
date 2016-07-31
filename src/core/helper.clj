(ns core.helper
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
            [ring.util.mime-type :as mime]))

;;------------------------helper------------------------

(defn sub-path 
  ([path] (sub-path (java.io.File. ".") path))
  ([parent path]
    (let [parent (if (instance? java.io.File parent) parent (java.io.File. parent))]
      (when (or (nil? path) (= "/" path) (= "" path) (.startsWith path "."))
        (throw (Exception. (str "bad name " path))))
      (let [target (java.io.File. parent path)]
        target))))

(defn bytes? [x]
  (when x
    (= (Class/forName "[B")
       (.getClass x))))

(defn is [a]
  (cond (nil? a) a
        (string? a) (java.io.ByteArrayInputStream. (.getBytes a))
        (instance? java.io.File a) (java.io.FileInputStream. a)
        (instance? java.io.InputStream a) a
        (bytes? a) (java.io.ByteArrayInputStream. a)
;        (instance? a java.io.Reader) (org.apache.common.io.input.ReaderInputStream. a)
        :otherwise (throw (Exception. (str "data needs to be of type string/reader/bytes/inpustream,
you provide: " (if a (type a) "null (no value)"))))))

;;------------------------log------------------------

(defn append-log
  [log-path message]
  (println message)
  (let [path (.getAbsolutePath (java.io.File. log-path))]
    (locking path
      (if-not
        (.exists (.getParentFile (new java.io.File path)))
        (.mkdirs (.getParentFile (new java.io.File path))))
      (with-open [w (jio/writer path :append true)
                  out (new java.io.PrintWriter w)]
        (.println out (str (java.util.Date.);(.format (new java.text.SimpleDataFormat "yyyy-MM-dd HH:mm:ss") (java.util.Date))
                           " " (if (string? message) message (pr-str message))))
        (.flush out)))))

(def primary-log-path "logs/virgo-primary-log.txt")

(def primary-log (partial append-log primary-log-path))

(defn log-ring-request
  [ring-request]
  (primary-log (str "[request] "
                    (:remote-addr ring-request) " "
                    (name (:request-method ring-request))
                    " " (:uri ring-request) )));(java.net.URIDecode/decode (:uri ring-requst) "UTF-8"))))

;;------------------------tunnel------------------------

(defonce tunnel (atom (comp/routes)))

(defonce tunnel-routes (atom []))

(defn extend-tunnel
  ([method expression callback]
    (extend-tunnel method expression {} callback))
  ([method expression match-map callback]
    (swap! tunnel-routes (fn[x] (vec (remove #(= (:expression %) expression) x))))
    (swap! tunnel-routes conj {:meth method :expression expression :callback callback})
    (reset! tunnel (apply comp/routes
                          (conj (vec (map (fn [{m :meth e :expression c :callback}]
                                            (comp/make-route m (clout/route-compile e match-map) c)) @tunnel-routes))
                                (route/not-found "Page not found"))))))
(defn run-tunnel
  ([ring-request]
    (let [uri (:uri ring-request) ;(java.net.URIDecoder.decode (:uri ring-request) "UTF-8")
          uri (if (.startsWith uri "/") uri (str "/" uri))]
      (log-ring-request ring-request)
      (@tunnel (assoc ring-request :uri uri))))
  ([method uri]
    (run-tunnel {:uri uri
                 :request-method (keyword method)
                 :server-port 20
                 :remote-addr "1.1.1.1"
                 :server-name "command-line"
                 :schema :http
                 :body System/in
                 :header {}})))

;;------------------------balancer------------------------

(defn command-line-balancer
  [method-query-pairs]
  (doseq [[method uri] method-query-pairs]
    (println (str "executing request"))
    (println (str "method : " method))
    (println (str "request : " uri))
    (let [{status :status body :body :as result} (run-tunnel method uri)]
          (println (str "status : " status))
          (jio/copy (is body) (or *out* System/out))
          (println ""))))

(defn web-balancer [ring-request]
  (try (run-tunnel ring-request)
    (catch Exception e
      {:body (str "failure during threading\r\n"
                  (with-out-str (.printStackTrace e (java.io.PrintWriter. *out*))))
       :status 500})))

;;------------------------runner------------------------

(def runners (atom {}))

(defn run-reserve [name]
  (when (get @runners name)
    (throw (Exception. (str name "has been rinning")))))

(defn run-cancel [name]
  (when-not (get @runners name)
    (throw (Exception. (str "no such runner: " name ))))
  (let [{k :kill r :restart} (get @runners name)]
    (when-not (and k (fn? k )) (throw (Exception. "cannot be stopped")))
    (swap! runners dissoc name)
    (k)))

(defn run-restart [name]
  (when-not (get @runners name)
    (throw (Exception. (str "no such runner: " name ))))
  (let [{k :kill r :restart} (get @runners name)]
    (when-not (and k r (fn? k) (fn? r)) (throw (Exception. "cannot be restarted.")))
    (swap! runners dissoc name)
    (try (k) (catch Throwable t "Swallowed"))
    (r)))

(defn run-register [name kill restart]
  (when (nil? kill) (throw (Exception. "kill can not be nil")))
  (when (nil? restart) (throw (Exception. "restart can not be nil")))
  (when-not (or (= :nokill kill) (fn? kill)) (throw (Exception. "kill must be function or :nokill")))
  (when-not (or (= :norestart restart) (fn? restart)) (throw (Exception. "restart must be function or :norestart")))
  (when (and (= :nokill kill) (fn? restart)) (throw (Exception. "kill cannot be :nokill because there is a restart")))
  (run-reserve name)
  (swap! runners assoc name {:kill kill
                             :restart restart}))

(defn run-complete [name]
  (swap! runners dissoc name))

;;------------------------nrepl------------------------

(defonce repl (atom nil))

(defn start-repl [port]
  (nrepl/start-server :port (Long/parseLong (str port))))

(defn stop-repl []
  (when-not (nil? @repl)
    (reset! repl nil)))