(ns pcp-server.routes.admin
  (:require
    [pcp-server.middleware :as middleware]))

(defn admin-routes []
[""
{:middleware [middleware/wrap-formats]}])