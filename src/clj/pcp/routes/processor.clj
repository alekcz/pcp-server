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
   [me.raynes.fs.compression :as compression]
   [clj-uuid :as uuid]
   [cheshire.core :as json]
   [crypto.password.scrypt :as password]
   [buddy.sign.jwt :as jwt]
   [buddy.core.hash :as hash]))
   

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
(def secret (hash/sha256 (str (uuid/v1))))

(defn get-config []
  (or (env :pcp-config) (try (slurp CONFIG) (catch Exception e nil)) DEFAULT-CONFIG))

(def config (atom (read-string (get-config))))

(defn load-config []
  (let [new-config (read-string (get-config))]
    (reset! config (merge new-config @config))))

(defn write-and-extract-zip [stream path]
  (with-open [w (io/output-stream (str path ".zip"))]
    (.write w stream))
  (compression/unzip (str path ".zip") path)
  (fs/delete (str path ".zip")))

(defn get-from-github [name data]
  (let [zip (str/replace (:github data) ".git" "/archive/master.zip")
        rando (uuid/v1) path (str ".pcp-sites/" rando)]
            (println (str "Loading: " (:github data)))
            (->
                  (client/get zip { :headers {:Authorization (str "token " (:token data))}
                                            :as :byte-array})
                  (:trace-redirects)
                  (first)
                  (client/get {:as :byte-array})
                  (:body)
                  (write-and-extract-zip path))
            (let [full-new-root (-> (fs/find-files path #".*?src") first .getPath)
                  new-root (str/replace full-new-root (str (.getCanonicalPath (clojure.java.io/file ".")) "/") "")]
              (swap! config assoc-in [:sites name :root] new-root))))

(defn process-sites []
  (doseq [site (-> @config :sites)]
    (let [name (first site) data (second site)]
      (cond 
        (contains? data :root)
          nil    
        (contains? data :github)
          (get-from-github name data)
          :else nil))))

(defn get-root [host]
  (:root ((keyword host) (:sites @config))))

(defn add-extension [path]
  (if (str/ends-with? path "/") 
      (str path "index.html")
      path))

(defn read-source [path]
  (try
    (str (slurp path))
    (catch java.io.FileNotFoundException fnfe nil)))

(defn load-source [pcp-params]
  (let [root (get-root (:server-name pcp-params))
        path (add-extension (:path pcp-params))]
    (str root path)))

(defn process-request [request]
  (let [pcp-params (extract-request-parameters request)
        root (get-root (:server-name pcp-params))
        path (-> pcp-params load-source)]
    (println path)
    (cond 
        (str/ends-with? path ".clj") (pcp/run (read-source path) :params pcp-params :root root)
        (str/ends-with? path ".html") (pcp/format-response 200 (io/file path) "text/html")
        (fs/exists? path) (pcp/file-response (io/file path))
        :else (pcp/format-response 404 nil nil))))

(defn request-handler [request]
  (try 
    (process-request request)
    (catch Exception e (pcp/format-response 500 nil nil))))


(defn rebuild [request]
  (let [server (-> request :params :server) site (get-in @config [:sites (keyword server)])
        name (first site) data (second site)]
    (println site)
    (get-from-github (keyword server) site)
    (pcp/format-response 200 "success" "text/plain")))

(defn auth [request]
  (let [user (-> request :params :user) token (-> request :params :token)]
    (if (password/check (env (keyword user)) token)
      (pcp/format-response 200 {:user user :token nil} "application/json")
      (pcp/format-response 403 {:error true} "application/json"))))

(defn debug [request]
  (pcp/format-response 200 (json/encode @config) "application/json"))

(defn process-routes []
  [""
   {:middleware [middleware/wrap-formats]}
   ["/pcp-admin/reload" {:handler rebuild}]
   ["/pcp-admin/debug" {:handler debug}]
   ["/pcp-admin/auth" {:handler auth}]
   ["*" {:handler process-request}]])

(defn init [] 
  (do
    (fs/delete-dir ".pcp-sites/")
    (fs/mkdir ".pcp-sites/")
    (process-sites)
    (at-at/every 20000 process-sites my-pool)
    (at-at/every 30000 load-config my-pool)))

(defn login [url password]
  (let [p (password/encrypt password)]
    (client/get)))