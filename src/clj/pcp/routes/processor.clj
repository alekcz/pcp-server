(ns pcp.routes.processor
  (:require
   [clojure.java.io :as io]
   [pcp.middleware :as middleware]
   [ring.util.response]
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


(defn append-extension [path]
  (if true
      (str "www/" path "/index.clj")
      path))

(defn read-source [path]
  (try
    (str (slurp (append-extension path)))
    (catch java.io.FileNotFoundException fnfe {:status 404 :body ""})))

(defn load-source [pcp-params]
  (let [host (:server-name pcp-params)
        path (:path pcp-params)]
    (read-source (str host path))))

(defn run-source [source pcp-params]
  (let [opts  { ;:namespaces {'cheshire.core cheshire.core}
                :bindings {'pcp (sci/new-var 'pcp pcp-params)}}]
    (if (map? source)
      source
      (sci/eval-string source opts))))    

(defn process-request [request]
  (let [pcp-params (extract-request-parameters request)
        result (->  pcp-params
                    (load-source)
                    (run-source pcp-params))]
    (if (map? result)           
      { :status (or (:status result) 200)
        :headers (merge {"Content-Type" "text/html"} (:headers result))
        :body (if (contains? result :body) (:body result) result)}
      { :status 200
        :headers {"Content-Type" "text/html"}
        :body result})))

(defn process-routes []
  [""
   {:middleware [middleware/wrap-formats]}
   ["*" {:handler process-request}]])

