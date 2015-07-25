(ns stravinski.streamer
  (:use
   [twitter.oauth]
   [twitter.callbacks]
   [twitter.callbacks.handlers]
   [twitter.api.streaming]
   [clojure.java.io :as io])
  (:require
   [cheshire.core]
   [clojure.string]
   [http.async.client :as ac])
  (:import
   (twitter.callbacks.protocols AsyncStreamingCallback)))

(def streamer-obj (atom nil) )
(def stream-processor-agent (agent (clojure.lang.PersistentQueue/EMPTY)))
(def params-storage-agent (atom nil))
(def warning-agent (atom nil))

(defn get-credentials [creds-map]
  (make-oauth-creds (:app-consumer-key creds-map)
                    (:app-consumer-secret creds-map)
                    (:user-access-token creds-map)
                    (:user-access-token-secret creds-map)))

(defn get-error []
  (:error @streamer-obj))

(defn agent-watch [agent-ref f w-key]
  (remove-watch stream-processor-agent w-key)
  (add-watch agent-ref w-key f))

(defn create-empty-queue [& rest]
  (clojure.lang.PersistentQueue/EMPTY))

(defn response-success-process [payload]
  (send stream-processor-agent conj (str payload)))

(defn watcher-fn [f key agent-ref old-state new-state]
  (let [matcher-fn (fn [el] (re-find (re-matcher #"\r\n" el)))]
    (if (not (nil? (matcher-fn (str (last new-state)))))
      (try
        (send agent-ref create-empty-queue)
        (let [json-str (cheshire.core/parse-string (clojure.string/join new-state) true)]
          (when (and (:text json-str )
                   ( not (:delete json-str) ))
            (f json-str))
          (when (:warning json-str)
            (reset! warning-agent json-str)))
        (catch Exception e (str "Exception:" (.getMessage e)))))))

(defn stop-streaming []
  (try 
    ((:cancel (meta @streamer-obj)))
    (catch Exception e (.getMessage e)))
  (reset! streamer-obj nil))

(defn attach-stream [processor-f creds-map track-params]
  (let [resp-promise (promise)
        cb (AsyncStreamingCallback.
            (fn [_resp payload]
              (send stream-processor-agent conj (str payload)))
            (fn [_resp])
            (fn [_resp ex] (.printStackTrace ex)))]

    (if (agent-error stream-processor-agent)
      (restart-agent stream-processor-agent (create-empty-queue)))

    (agent-watch stream-processor-agent
                 (partial watcher-fn processor-f)
                 :processor)

    (if (not (nil? @streamer-obj))
      (stop-streaming))
    (println "Starting with params:" track-params)

    (reset! params-storage-agent track-params)

    (let [default-params {:stall_warnings true
                          :ouath-creds (get-credentials creds-map)
                          :callbacks cb}]
      (if (empty? track-params)
        (reset! streamer-obj (statuses-sample :oauth-creds (get-credentials creds-map) :callbacks cb))
        (reset! streamer-obj (statuses-filter (assoc default-params :params {:language "en" ,:track track-params})))))))
