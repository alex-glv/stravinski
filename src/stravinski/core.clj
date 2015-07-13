(ns stravinski.core
  (:require [stravinski.streamer :as streamer]
            [riemann.client :as riemann])
  (:import
   [ java.util Properties ])
  (:gen-class))

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

(def riemann-conn (atom nil))

(defn riemann-submit [tweet]
  (if-let [hashtags (:hashtags (:entities tweet))]
    (doseq [hashtag hashtags]
      (riemann.client/send-event @riemann-conn {:service "tweet.hashtag"
                                                :description (:text hashtag)
                                               })))

  (if-let [followers (:followers_count (:user tweet))]
    (riemann.client/send-event @riemann-conn {:service "tweet.followers"
                                              :metric followers }))

  (riemann.client/send-event @riemann-conn {:service "tweet.meta"
                                            :metric (:events_sent tweet)
                                            })

  (riemann.client/send-event @riemann-conn {:service "tweet.text"
                                            :description (:text tweet)
                                            :metric (count (:text tweet))
                                            })
  )
  

(defn start-feeding [f]
  (let [creds-map {:app-consumer-key (assert-get "app.consumer.key")
                   :app-consumer-secret (assert-get "app.consumer.secret")
                   :user-access-token (assert-get "user.access.token")
                   :user-access-token-secret (assert-get "user.access.token.secret")
                   :filter {:track  (assert-get "twitter.filter.track")}}
        processor-fn (partial (fn [f to-deliver-err to-deliver-succ counter tweet]
                                (try 
                                  (send (var-get counter) inc)
                                  ((var-get f) (assoc  tweet :events_sent @(var-get counter)))
                                  (catch Exception e (deliver to-deliver-err (.getMessage e))))
                                (deliver to-deliver-succ tweet))
                              #'riemann-submit #'errors? #'success? #'stats-agent)]
    (if (nil? @riemann-conn)
      (reset! riemann-conn (riemann.client/tcp-client :host (str (assert-get "riemann.host")))))
    (f processor-fn creds-map)))
