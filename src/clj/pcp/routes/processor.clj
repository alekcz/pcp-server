(ns pcp.routes.processor
  (:require
   [pcp.middleware :as middleware]
   [pcp.engine :as pcp]
   [clojure.string :as str]
   [overtone.at-at :as at-at]
   [digest :as digest]
   [clojure.java.io :as io]
   [clj-http.client :as client]
   [environ.core :refer [env]]
   [me.raynes.fs :as fs]
   [me.raynes.fs.compression :as compression]))
   

(defn extract-request-parameters [request] ;malli can make this nicer
  { :request-method (:request-method request)
    :server-port (:server-port request)
    :server-name (:server-name request)
    :path (:uri request)
    :host (:host request)
    :remote-addr (:remote-addr request)
    :protocol (:protocol request)
    :ssl-client-cert (:ssl-client-cert request)
    :headers (:headers request)
    :content-length (:content-length request)
    :form-params (:form-params request)
    :query-params (:query-params request)
    :params (:params request)
    :multipart-params (:multipart-params request)
    :query-string (:query-string request)
    :session (:session request)
    :cookies (:cookies request)
    :anti-forgery-token (:anti-forgery-token request)})

(def CONFIG "./pcp.edn")
(def DEFAULT-CONFIG "{:sites {:localhost {:root \"src\"}}}")
(def my-pool (at-at/mk-pool))

(defn get-config []
  (or (env :pcp-config) (try (slurp CONFIG) (catch Exception e nil)) DEFAULT-CONFIG))

(def config (atom (read-string (get-config))))

(defn load-config []
  (let [new-config (read-string (get-config))]
    (println new-config)
    (reset! config (merge new-config @config))))

(defn write-and-extract-zip [stream path]
  (with-open [w (io/output-stream (str path ".zip"))]
    (.write w stream))
  (compression/unzip (str path ".zip") path)
  (fs/delete (str path ".zip")))

(defn process-sites []
  (doseq [site (-> @config :sites)]
    (println site)
    (let [name (first site)
          data (second site)]
      (cond 
        (contains? data :root)
          nil    

        (contains? data :github)
          (let [zip (str/replace (:github data) ".git" "/archive/master.zip")
                path (str ".pcp-sites/" (digest/sha-256 zip))]
            (->
                  (client/get zip { :headers {:Authorization (str "token " (:token data))}
                                            :as :byte-array})
                  (:trace-redirects)
                  (first)
                  (client/get {:as :byte-array})
                  (:body)
                  (write-and-extract-zip path))
            (let [new-root (-> (fs/find-files path #".*?src") first str (str/replace (str (.getCanonicalPath (clojure.java.io/file ".")) "/") ""))]
              (swap! config assoc-in [:sites name :root] new-root)))

          :else nil))))

(defn get-root [host]
  (:root ((keyword host) (:sites @config))))

(defn add-extension [path]
  (if (str/ends-with? path "/") 
      (str path "index.clj")
      path))

(defn read-source [path]
  (try
    (let [final-path (add-extension path)]
      (str (slurp final-path)))
    (catch java.io.FileNotFoundException fnfe nil)))

(defn load-source [pcp-params]
  (let [root (get-root (:server-name pcp-params))
        path (:path pcp-params)]
    (str root path)))

(defn process-request [request]
  (let [pcp-params (extract-request-parameters request)
        root (get-root (:server-name pcp-params))
        path (-> pcp-params load-source)]
    (println path)
    (if (str/ends-with? (:path pcp-params) ".clj")
        (pcp/run (read-source path) :params pcp-params :root root)
        (if (fs/exists? path)
            (pcp/file-response (io/file path))
            (pcp/format-response 404 nil nil)))))

(defn request-handler [request]
  (try 
    (process-request request)
    (catch Exception e (pcp/format-response 500 nil nil))))

(defn process-routes []
  [""
   {:middleware [middleware/wrap-formats]}
   ["*" {:handler process-request}]])


(defn init [] 
  (do
    (fs/delete-dir ".pcp-sites/")
    (fs/mkdir ".pcp-sites/")
    (process-sites)
    (at-at/every 20000 process-sites my-pool)
    (at-at/every 30000 load-config my-pool)))
