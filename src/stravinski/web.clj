(ns stravinski.web
  (:require [net.cgrand.enlive-html :as html :only [defsnippet deftemplate content]]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.params :as params]
            [ring.util.response :as resp]
            [stravinski.core :as core]
            [stravinski.streamer :as streamer])
  (:gen-class))

(defonce ^:dynamic server-instance (atom nil))


(defn start-server [routes]
  (let [out *out*]
    (binding [*out* out]
      (if (not (nil? @server-instance))
        (.stop @server-instance))
      (reset! server-instance  (jetty/run-jetty (params/wrap-params routes) { :port 7705 :join? false }))
      server-instance)))

(html/defsnippet status "templates/status.html" [:div#status-content]
  [{:keys [total-sent http-status started-on]}]
  [:#total-sent] (html/content (str total-sent))
  [:#http-status] (html/content (str http-status))
  [:#started-on] (html/content (str started-on)))

(html/defsnippet control "templates/control.html" [:ul] [])

(html/deftemplate index "templates/index.html"
  [title overview]
  [:head :title ] (html/content title)
  [:#menu] (html/content  (control))
  [:#status] (html/content (status overview)))

(def ^:dynamic *start-fn* #(if (nil? @streamer/streamer-obj)
                             (core/start-feeding streamer/attach-stream)))

(def ^:dynamic *stop-fn* #(if (not (nil? @streamer/streamer-obj))
                           (streamer/stop-streaming)))

(defn handler [request]
  (if (= (:uri request)
         "/start")
    (*start-fn*))
  (if (= (:uri request)
         "/stop")
    (*stop-fn*))
  (let [overview-map {:total-sent @core/stats-agent
                      :http-status (if (nil? @streamer/streamer-obj)
                                     "Not running"
                                     (str (:code @(:status @streamer/streamer-obj))
                                          "/"
                                          (:msg @(:status @streamer/streamer-obj))))
                      :started-on (if (nil? @streamer/streamer-obj)
                                    "-"
                                    (:date @(:headers @streamer/streamer-obj)))}]
    (resp/response (index "Stream control" overview-map))))

(defn -main
  [& args]
  (start-server #'handler))

