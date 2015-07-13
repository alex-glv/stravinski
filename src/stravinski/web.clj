(ns stravinski.web
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.params :as params]
            [ring.util.response :as resp]
            [stravinski.core :as core]
            [ring.middleware.json :only [wrap-json-response]]
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
                                     "not-running"
                                     (str (:code @(:status @streamer/streamer-obj))
                                          "/"
                                          (:msg @(:status @streamer/streamer-obj))))
                      :started-on (if (nil? @streamer/streamer-obj)
                                    "-"
                                    (:date @(:headers @streamer/streamer-obj)))}]
    (resp/response overview-map)))

(defn -main
  [& args]
  (start-server (ring.middleware.json/wrap-json-response handler)))

