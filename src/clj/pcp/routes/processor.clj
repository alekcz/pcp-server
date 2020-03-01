(ns pcp.routes.processor
  (:require
   [pcp.layout :as layout]
   [clojure.java.io :as io]
   [pcp.middleware :as middleware]
   [ring.util.response]
   [ring.util.http-response :as response]
   [cheshire.core :as json]))

(defn extract-request-parameters [request] ;malli can make this nicer
  { :request-method (:request-method request)
    :server-port (:server-port request)
    :server-name (:server-name request)
    :uri (:uri request)
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

(defn process-request [request]
  (let [pcp-params (extract-request-parameters request)]
    (println pcp-params)
    {:status 200
    :headers {"Content-Type" "application/json"}
    :body (json/encode pcp-params)}))

(defn process-routes []
  [""
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/" {:get process-request}]])

