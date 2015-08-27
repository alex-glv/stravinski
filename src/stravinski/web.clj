(ns stravinski.web
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.params :as params]
            [ring.util.response :as resp]
            [stravinski.core :as core]
            [stravinski.streamer :as streamer])
  (:use [ring.middleware.json :only [wrap-json-response wrap-json-body]])
  (:gen-class))

(defonce ^:dynamic server-instance (atom nil))

(defn start-server [routes]
  (let [out *out*]
    (binding [*out* out]
      (if (not (nil? @server-instance))
        (.stop @server-instance))
      (reset! server-instance  (jetty/run-jetty (-> routes
                                                    wrap-json-response 
                                                    wrap-json-body
                                                    params/wrap-params)
                                                { :port 7705 :join? false }))
      server-instance)))

(def ^:dynamic *start-fn* #(if (nil? @streamer/streamer-obj)
                             (core/start-feeding streamer/attach-stream %)))

(def ^:dynamic *stop-fn* #(if (not (nil? @streamer/streamer-obj))
                           (streamer/stop-streaming)))

;; api functs
(defmulti handle-api-call (fn [request] (:uri request)))

(defmethod handle-api-call "/start" [request]
  (*start-fn* (get-in request [:body "track"]))
  {:status "success"})

(defmethod handle-api-call "/stop" [request]
  (*stop-fn*)
  {:status "success"})

(defmethod handle-api-call "/riemann-connect" [request]
  (let [host (get-in request [:body "riemann-host"])
          port (get-in request [:body "riemann-port"])]
      (if (or (not host) (not port))
        {:status "Incorrect riemann configuration provided"}
        (let [res (core/open-rconn host port)]
          {:status "Connection restarted"
           :connection (riemann.client/connected? @core/riemann-conn)}))))

(defmethod handle-api-call :default [request]
  (println request)
  {:methods ["/start" "/stop" "/riemann-connect"]})

;; handler 
(defn handler [request]
  (let [method-call (handle-api-call request)
        overview-map {:total-sent @core/stats-agent
                      :http-status (if (nil? @streamer/streamer-obj)
                                     ""
                                     (str (:code @(:status @streamer/streamer-obj))
                                          "/"
                                          (:msg @(:status @streamer/streamer-obj))))
                      :started-on (if (nil? @streamer/streamer-obj)
                                    ""
                                    (:date @(:headers @streamer/streamer-obj)))
                      :track @streamer/params-storage-agent
                      :warning @streamer/warning-agent
                      :pusher-connected (or (nil? @core/riemann-conn)
                                            (riemann.client/connected? @core/riemann-conn))
                      :api-response method-call
                      }]
    (resp/response overview-map)))

(defn -main
  [& args]
  (println "Opening riemann conn")
  (try
    (core/open-rconn)
    (catch Exception e (.printStackTrace e)))
  (println "Starting web")
  (start-server handler))

