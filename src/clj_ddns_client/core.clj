(ns clj-ddns-client.core
  (:require [clojure.edn :as edn]
            [clojure.core.async :as a]
            [clj-time.core :as t]
            [clj-time.periodic :as p]
            [chime :refer [chime-at chime-ch]]
            [taoensso.timbre :as log]
            [taoensso.timbre.appenders.core :as appenders]
            [clj-ddns-client.providers.core :as provider]
            ;; provider implementations
            clj-ddns-client.providers.dnsever)
  (:gen-class))

(defn- make-provider-updater!
  "Make an alarm channel and a new thread that updates the given provider.
  Returns the alarm channel.
  times = A sequence of datetime
  provider = One of :providers in config.edn"
  [times provider]
  (let [chimes (chime-ch times
                         {:ch (-> 1 a/sliding-buffer a/chan)})]
    (a/go-loop []
      (when-let [time (a/<! chimes)]
        (try
          (provider/update! provider)
          (catch Exception e
            (log/error e)
            (a/close! chimes)))
        (recur)))
    chimes))

(defn- start-updating!
  "Returns alarm channels that signal ddns entries every :update-interval seconds.
  Close the channels to cancel schedules.
  config = config.edn"
  [config]
  (let [times (p/periodic-seq (t/now)
                              (-> (:update-interval config) t/seconds))
        providers (:providers config)]
    (doall (map (partial make-provider-updater! times)
                providers))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [config (-> "config.edn" slurp edn/read-string)
        update-alarms (start-updating! config)]
    (log/set-level! (:log-level config))
    (try
      (.join (Thread/currentThread))
      (finally
        (println "Interrupted")
        (doseq [alarm update-alarms]
          (a/close! alarm))))))
