(ns stravinski.core
  (:require [stravinski.streamer :as streamer]
            [riemann.client :as riemann])
  (:import
   ( java.util Properties ) )
  (:gen-class))

(defn -main
  [& args]

  )

(def stats-agent (agent 0))
(def errors? (promise))
(def success? (promise))

(defn load-config-file
  "this loads a config file from the classpath"
  [file-name]
  (let [file-reader (.. (Thread/currentThread)
                        (getContextClassLoader)
                        (getResourceAsStream file-name))
        props (Properties.)]
    (.load props file-reader)
    (into {} props)))

(def ^:dynamic *config* (load-config-file "creds.config"))

(defn assert-get
  "get a value from the config, otherwise throw an exception detailing the problem"
  [key-name]
  (or (get *config* key-name) 
      (throw (Exception. (format "please define %s in the resources/test.config file" key-name)))))

(def riemann-conn (riemann.client/tcp-client :host (assert-get "riemann.host")))

(defn riemann-submit [tweet]
  (riemann.client/send-event riemann-conn {:service "tweets"
                                           :tweet (:text tweet)
                                           }))

(defn start-feeding [f]
  (let [creds-map {:app-consumer-key (assert-get "app.consumer.key")
                   :app-consumer-secret (assert-get "app.consumer.secret")
                   :user-access-token (assert-get "user.access.token")
                   :user-access-token-secret (assert-get "user.access.token.secret")}
        processor-fn (partial (fn [f to-deliver-err to-deliver-succ counter tweet]
                                (try 
                                  (send (var-get counter) inc)
                                  ((var-get f) tweet)
                                  (catch Exception e (deliver to-deliver-err (.getMessage e))))
                                (deliver to-deliver-succ tweet))
                              #'riemann-submit #'errors? #'success? #'stats-agent)]
    (f processor-fn creds-map)))
