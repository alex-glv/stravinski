(ns stravinski.streamer
  (:use
   [twitter.oauth]
   [twitter.callbacks]
   [twitter.api.restful]
   [twitter.callbacks.handlers]
   [twitter.api.streaming]
   [clojure.java.io :as io])
  (:require
   [cheshire.core]
   [clojure.string]
   [clojure.data.json :as json]
   [http.async.client :as ac])
  (:import
   (java.io OutputStream)
   (java.util Properties)
   (twitter.callbacks.protocols AsyncStreamingCallback)))

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

(def ^:dynamic *app-consumer-key* (assert-get "app.consumer.key"))
(def ^:dynamic *app-consumer-secret* (assert-get "app.consumer.secret"))
(def ^:dynamic *user-access-token* (assert-get "user.access.token"))
(def ^:dynamic *user-access-token-secret* (assert-get "user.access.token.secret"))

(def my-creds (make-oauth-creds *app-consumer-key*
                                *app-consumer-secret*
                                *user-access-token*
                                *user-access-token-secret*))

(defn get-error [res-obj]
  (:error res-obj))

(def stream-processor-agent (agent (clojure.lang.PersistentQueue/EMPTY)))

(defn agent-watch [agent-ref f w-key]
  (remove-watch stream-processor-agent w-key)
  (add-watch agent-ref w-key f))

(defn create-empty-queue [& rest]
  (clojure.lang.PersistentQueue/EMPTY))

(defn response-success-process [payload]
  (send stream-processor-agent conj (str payload)))

(defn watcher-fn [w key agent-ref old-state new-state]
  (let [matcher-fn (fn [el] (re-find (re-matcher #"\r\n" el)))]
    (if (not (nil? (matcher-fn (str (last new-state)))))
      (try
        (send agent-ref create-empty-queue)
        (let [json-str (cheshire.core/parse-string (clojure.string/join new-state) true)]
          (if (and (:text json-str )
                   ( not (:delete json-str) ))
            (.write w (clojure.string/join new-state);; (:text json-str)
                    )))
        (catch Exception e (str "Exception:" (.getMessage e)))))))

(defn attach-stream []
  (let [resp-promise (promise)
        w (writer "map.txt")
        cb (AsyncStreamingCallback.
            (fn [_resp payload]
              (send stream-processor-agent conj (str payload))
              )
            (fn [_resp]
              (.close w)
              )
            (fn [_resp ex]
              (.close w)
              (.printStackTrace ex)))]

    (if (agent-error stream-processor-agent)
      (restart-agent stream-processor-agent (create-empty-queue)))

    (agent-watch stream-processor-agent
                 (partial watcher-fn w)
                 :processor)
    (statuses-sample
     ;; :params {:track "storm,bad weather,good weather,rain,sun,sunny,snow,freezing"}
     :oauth-creds my-creds
     :callbacks cb)
    ))

(defn stop-streaming [res-obj]
  ((:cancel (meta res-obj))))
