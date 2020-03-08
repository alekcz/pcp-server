(ns pcp.engine
  (:require
   [sci.core :as sci]
   [stasis.core :as stasis]
   [ring.util.response :as resp]
   [clojure.java.io :as io]
   [clojure.string :as str]
   ;the below are included to force deps to download
   [hiccup.core :as hiccup]
   [cheshire.core :as json]
   [selmer.parser :as selmer-parser]
   [selmer.filters :as selmer-filters]
   [clj-http.client :as client]
   [tick.alpha.api :as t]))

(defn extract-namespace [namespace]
  (into {} (ns-publics namespace)))

(def namespaces
  { 'hiccup.core {'html (with-meta @#'hiccup/html {:sci/macro true})}
    'cheshire.core (extract-namespace 'cheshire.core)
    'selmer.parser (extract-namespace 'selmer.parser)
    'selmer.filters (extract-namespace 'selmer.filters)
    'clj-http.client (extract-namespace 'clj-http.client)
    'tick.alpha.api (extract-namespace 'tick.alpha.api)})

(defn format-response [status body mime-type]
  (-> (resp/response body)    
      (resp/status status)
      (resp/content-type mime-type)))   

(defn file-response [body]
  (if (resp/response? body)
    body
    (-> (resp/response body)    
        (resp/status 200))))   

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

(defn run [source &{:keys [root params]}]
  (if (string? source)
    (let [includes (stasis/slurp-directory (or root (.getCanonicalPath (clojure.java.io/file "."))) #"(\.clj$|\.sql$)")
            opts  { :namespaces namespaces
                    :bindings { 'pcp (sci/new-var 'pcp params)
                                'include identity
                                'response (sci/new-var 'response format-response)}}
            full-source (process-includes includes source)]
        (sci/eval-string full-source opts))
    (format-response 404 nil nil)))