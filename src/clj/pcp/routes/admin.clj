(ns pcp.routes.admin
  (:require
    [pcp.middleware :as middleware]))

(defn admin-routes []
[""
{:middleware [middleware/wrap-formats]}])