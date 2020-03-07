(ns pcp.routes.processor
  (:require
   [pcp.middleware :as middleware]
   [pcp.helpers :as helpers]
   [ring.util.response :as resp]
   [sci.core :as sci]
   [clojure.string :as str]
   [stasis.core :as stasis]
   ;the below are included to force deps to download
   [cheshire.core :as json]
   [selmer.parser :as selmer-parser]
   [selmer.filters :as selmer-filters]
   [clj-http.client :as client]
   ))

(defn extract-namespace [namespace]
  (into {} (ns-publics namespace)))

(def namespaces
  {'cheshire.core (extract-namespace 'cheshire.core)
   'selmer.parser (extract-namespace 'selmer.parser)
   'selmer.filters (extract-namespace 'selmer.filters)
   'clj-http.client (extract-namespace 'clj-http.client)})

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


(def config (atom {:servers {:localhost {:root "www/127.0.0.1"}}}))

(defn get-root [host]
  (:root ((keyword host) (:servers @config))))

(defn add-extension [path]
  (if (str/ends-with? path "/") 
      (str path "index.clj")
      path))

(defn format-response [status body mime-type]
  (-> (resp/response body)    
      (resp/status status)
      (resp/content-type mime-type)))   

(defn file-response [body]
  (if (resp/response? body)
    body
    (-> (resp/response body)    
        (resp/status 200))))   

(defn read-source [path]
  (try
    (let [final-path (add-extension path)]
      (str (slurp final-path)))
    (catch java.io.FileNotFoundException fnfe (format-response 404 nil :plain))))


(defn load-source [pcp-params]
  (let [host (get-root (:server-name pcp-params))
        path (:path pcp-params)]
    (str host path)))

(defn process-includes [includes source]
  (let [includes-used (re-seq #"\(include\s*?\"(.*?)\"\s*?\)" source)]
    (loop [code source externals includes-used]
      (if (empty? externals)
        code
        (let [included (get includes (-> externals first second))]
          (if (nil? included)
            (throw 
              (ex-info (str "Included file '" (-> externals first second) "' was not found.")
                        {:cause   (str (-> externals first first))}))
            (recur 
              (str/replace code (-> externals first first) included) 
              (rest externals))))))))

(defn run-source [source pcp-params]
  (let [includes (stasis/slurp-directory (get-root (:server-name pcp-params)) #"\.clj$")
        opts  { :namespaces namespaces
                :bindings { 'pcp (sci/new-var 'pcp pcp-params)
                            'include identity
                            'response (sci/new-var 'response format-response)}}
        full-source (process-includes includes source)]
    (sci/eval-string full-source opts)))

(defn process-request [request]
  (let [pcp-params (extract-request-parameters request)
        source (->  pcp-params (load-source) (read-source))]
    (cond
      (str/ends-with? (:path pcp-params) "/")  (run-source source pcp-params)
      (str/ends-with? (:path pcp-params) ".clj") (run-source source pcp-params)
      :else (file-response source))))

(defn request-handler [request]
  (try 
    (process-request request)
    (catch Exception e (format-response 500 nil :plain))))

(defn process-routes []
  [""
   {:middleware [middleware/wrap-formats]}
   ["*" {:handler process-request}]])

(def pcp-params "")
(def variable-here "")