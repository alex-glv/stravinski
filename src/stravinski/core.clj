(ns stravinski.core
  (:require [stravinski.streamer :as streamer]
            [riemann.client :as riemann]
            [clojure.string :as str])
  (:import
   [ java.util Properties ])
  (:gen-class))

(def stats-agent (agent 0))
(def errors? (promise))
(def success? (promise))

(defn mappify [mymap]
  (apply hash-map (interleave (map str (range (count mymap))) mymap)))

(defn dottify-reduce [root-map str-acc]
  (reduce-kv (fn [init k v]
               (let [new-str-acc (if (empty? str-acc)
                                   (name k)
                                   (str/join "." [str-acc (name k)]))]
                 (conj init (cond
                              (map? v) #(dottify-reduce v new-str-acc)
                              (vector? v) #(dottify-reduce (mappify v) new-str-acc)
                              :else {(keyword new-str-acc) (str v)})))) [] root-map))

(defn dottify
  ([root-map] (dottify [] [#(dottify-reduce root-map "")]))
  ([acc exec-calls]
   (if (empty? exec-calls)
     acc
     (let [new-buckets (mapcat (fn [f] (f)) exec-calls)]
       (recur (into acc (filter (complement fn?) new-buckets))
              (filter fn? new-buckets))))))


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
      nil))

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

  (let [x (apply merge (-> tweet
                           (assoc :service "tweet.full")
                           (dissoc :entities)
                           (dissoc :extended_entities)
                           dottify
                           ))
        lx (if (empty? (:geo.coordinates.0 x))
             x
             (assoc x :location (str/join "," [(:geo.coordinates.0 x) (:geo.coordinates.1 x)])))]
    (riemann.client/send-event @riemann-conn lx))
)

(defn start-feeding [f track-params]
  (let [creds-map {:app-consumer-key (or (System/getenv "APP_CONS_KEY") (assert-get "app.consumer.key"))
                   :app-consumer-secret (or (System/getenv "APP_CONS_SECRET") (assert-get "app.consumer.secret"))
                   :user-access-token (or (System/getenv "APP_ACC_TOKEN") (assert-get "user.access.token"))
                   :user-access-token-secret (or (System/getenv "APP_ACC_TOKEN_SECRET") (assert-get "user.access.token.secret"))
                   :filter {:track  (or (System/getenv "FILTER_TRACK") (assert-get "twitter.filter.track"))}}
        processor-fn (partial (fn [f to-deliver-err to-deliver-succ counter tweet]
                                (try 
                                  (send (var-get counter) inc)
                                  ((var-get f) (assoc  tweet :events_sent @(var-get counter)))
                                  (catch Exception e (deliver to-deliver-err (.getMessage e))))
                                (deliver to-deliver-succ tweet))
                              #'riemann-submit #'errors? #'success? #'stats-agent)]
    (if (nil? @riemann-conn)
      (reset! riemann-conn (riemann.client/tcp-client :host (or (System/getenv "RIEMANN_HOST") (assert-get "riemann.host"))
                                                      :port (or (System/getenv "RIEMANN_PORT") (read-string (assert-get "riemann.port"))))))
    (binding [*out* *out*]
      (f processor-fn creds-map track-params))))
