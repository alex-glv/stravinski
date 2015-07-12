(ns stravinski.web
  (:require [net.cgrand.enlive-html :as html]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.params :as params]
            [ring.util.response :as resp]))

(defonce ^:dynamic server-instance (atom nil))

(defn start-server [routes]
  (if (not (nil? @server-instance))
    (.stop @server-instance))
  (reset! server-instance  (jetty/run-jetty (params/wrap-params routes) { :port 7705 :join? false }))
  server-instance)

(html/defsnippet status "templates/status.html" [:div#status-content]
  [{:keys [total-sent http-status last-tweet]}]
  [:#total-sent] (html/content total-sent)
  [:#http-status] (html/content http-status)
  [:#last-tweet] (html/content last-tweet))

(html/defsnippet control "templates/control.html" [:ul] [])

(html/deftemplate index "templates/index.html"
  [title]
  [:head :title ] (html/content title)
  [:#menu] (html/content  (control))
  [:#status] (html/content (status {:total-sent "" :http-status "" :last-tweet ""}))
  )

(defn handler [request]
  (resp/response (index "Stream control"))
  )

