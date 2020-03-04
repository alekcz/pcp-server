(ns pcp.routes.processor
  (:require
   [clojure.java.io :as io]
   [pcp.middleware :as middleware]
   [ring.util.response :as resp]
   [ring.util.http-response :as response]
   [cheshire.core :as json]
   [sci.core :as sci]
   [clojure.string :as str]))

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
    ;:body (:body request)
    :form-params (:form-params request)
    :query-params (:query-params request)
    :params (:params request)
    :multipart-params (:multipart-params request)
    :query-string (:query-string request)
    :session (:session request)
    :cookies (:cookies request)
    :anti-forgery-token (:anti-forgery-token request)})


(def config (atom {:localhost {:root "www/127.0.0.1"}}))
(def mime-types { :json   { :content-type "application/json"  
                            :encoder json/encode}
                  :plain  { :content-type "text/plain"        
                            :encoder str}
                  :text   { :content-type "text/plain"        
                            :encoder str}
                  :html   { :content-type "text/html"         
                            :encoder str}
                  :csv    { :content-type "text/csv"        
                            :encoder str}})

(defn get-root [host]
  (:root ((keyword host) @config)))

(defn add-extension [path]
  (if (str/ends-with? path "/") 
      (str path "index.clj")
      path))

(defn format-response [code body mime-type]
  (let [status code
        header (-> mime-types mime-type :content-type)
        body ((-> mime-types mime-type :encoder) body)]
   (-> (resp/response body)    
       (resp/status status)
       (resp/content-type header))))

(defn read-source [path]
  (try
    (let [final-path (add-extension path)]
      (println final-path)
      (str (slurp final-path)))
    (catch java.io.FileNotFoundException fnfe (format-response 404 nil :plain))))

(defn load-source [pcp-params]
  (let [host (get-root (:server-name pcp-params))
        path (:path pcp-params)]
    (read-source (str host path))))

(defn run-source [source pcp-params]
  (let [opts  { ;:namespaces {'cheshire.core cheshire.core}
                :bindings {'pcp (sci/new-var 'pcp pcp-params)
                           'response (sci/new-var 'response format-response)}}]
    (sci/eval-string source opts)))

(defn process-request [request]
  (let [pcp-params (extract-request-parameters request)
        result (->  pcp-params
                    (load-source)
                    (run-source pcp-params))]
    (println result)
    result))

(defn request-handler [request]
  (try 
    (process-request request)
    (catch Exception e (format-response 500 nil :plain))))

(defn process-routes []
  [""
   {:middleware [middleware/wrap-formats]}
   ["*" {:handler process-request}]])

