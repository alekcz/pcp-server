(ns pcp.handler
  (:require
    [pcp.middleware :as middleware]
    [pcp.layout :refer [error-page]]
    [pcp.routes.processor :refer [process-routes]]
    [pcp.routes.admin :refer [admin-routes]]
    [reitit.ring :as ring]
    [ring.middleware.content-type :refer [wrap-content-type]]
    [ring.middleware.webjars :refer [wrap-webjars]]
    [pcp.env :refer [defaults]]
    [mount.core :as mount]))

(mount/defstate init-app
  :start ((or (:init defaults) (fn [])))
  :stop  ((or (:stop defaults) (fn []))))

(mount/defstate app-routes
  :start
  (ring/ring-handler
    (ring/router
      [ (admin-routes)
        (process-routes)]
      {:conflicts nil})
    (ring/routes
      (ring/create-resource-handler
        {:path "/"})
      (wrap-content-type
          (wrap-webjars (constantly nil))))))

(defn app []
  (middleware/wrap-base #'app-routes))
