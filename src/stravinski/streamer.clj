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

(defn get-credentials [creds-map]
  (make-oauth-creds (:app-consumer-key creds-map)
                    (:app-consumer-secret creds-map)
                    (:user-access-token creds-map)
                    (:user-access-token-secret creds-map)))

(defn get-error []
  (:error @streamer-obj))

(def stream-processor-agent (agent (clojure.lang.PersistentQueue/EMPTY)))
(def params-storage-atom (atom nil))

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
          (if (and (:text json-str )
                   ( not (:delete json-str) ))
            (f json-str)))
        (catch Exception e (str "Exception:" (.getMessage e)))))))

(defn stop-streaming []
  ((:cancel (meta @streamer-obj)))
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

    (reset! params-storage-atom (or track-params
                                   (:track (:filter creds-map))
                                   ["cats"]))

    (reset! streamer-obj (statuses-filter
                          :params {:language "en" ,
                                   :track (or track-params
                                              (:track (:filter creds-map)))}
                          :oauth-creds (get-credentials creds-map)
                          :callbacks cb))))
