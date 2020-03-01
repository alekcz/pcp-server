(ns pcp.env
  (:require
    [selmer.parser :as parser]
    [clojure.tools.logging :as log]
    [pcp.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[pcp started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[pcp has shut down successfully]=-"))
   :middleware wrap-dev})
