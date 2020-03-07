(ns pcp.routes.admin
  (:require
    [clojure.java.io :as io]
    [pcp.middleware :as middleware]
    [ring.util.response :as resp]
    [ring.util.http-response :as response]
    [cheshire.core :as json]
    [sci.core :as sci]
    [clojure.string :as str]
    [rum.core :as rum]))

(def input-classes ["shadow" "appearance-none" "border" "rounded" "w-full" "py-2" "px-3" "text-gray-700" "leading-tight" "focus:outline-none" "focus:shadow-outline"])
(def label-classes ["block" "text-gray-700" "text-sm" "font-bold" "mb-2"])
(def primary-classes ["bg-blue-500" "hover:bg-blue-700" "text-white" "font-bold" "py-2" "px-4" "rounded" "focus:outline-none" "focus:shadow-outline"])
(def form-classes ["bg-white" "shadow-md" "rounded" "px-8" "pt-6" "pb-8" "mb-12"])

(rum/defc base-page [children]
  [:html
    [:head
      [:link {:href "/css/tailwind.min.css" :rel "stylesheet"}]]
    [:body 
      [:div.mx-auto.h-screen.bg-gray-200
        [:div 
          [:div "logo"]
          [:div "space"]
          [:div "menu"]]
        [:div.p-20.flex.items-center.justify-center children]]]])

(defn make-response [body]
  (-> (base-page body)
      (rum/render-static-markup)
      (resp/response)
      (resp/status 200)
      (resp/content-type "text/html")))

(defn hello [request]
  (->
    (resp/response "pew pew")
    (resp/status 200)
    (resp/content-type "text/plain")))

(defn login-page [request]  
  (make-response
    [:div.flex
      [:form {:class form-classes}
        [:div.mb-6
          [:label {:class label-classes} "Email"]
          [:input 
            { :class input-classes
              :type "email"}]]
        [:div.mb-4
          [:label {:class label-classes} "Password"]
          [:input 
            { :class input-classes
              :type "password"}]]
        [:input 
          { :class primary-classes
            :type "submit"}]]]))


(defn admin-routes []
[""
{:middleware [middleware/wrap-formats]}
["/pcp-admin/view/login" {:get login-page}]
["/pcp-admin/api/hello" {:get hello}]])