(ns pcp.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[pcp started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[pcp has shut down successfully]=-"))
   :middleware identity})
