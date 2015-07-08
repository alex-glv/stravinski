(ns stravinski.core
  (:require [stravinski.streamer :as streamer]
            [riemann.client :as riemann])
  (:import
   ( java.util Properties ) )
  (:gen-class))

(defn -main
  [& args]

  )
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

(defn start-feeding []
  (let [creds-map {:app-consumer-key (assert-get "app.consumer.key")
                   :app-consumer-secret (assert-get "app.consumer.secret")
                   :user-access-token (assert-get "user.access.token")
                   :user-access-token-secret (assert-get "user.access.token.secret")} ]
    (streamer/attach-stream #((.write *out* %)) creds-map )))
