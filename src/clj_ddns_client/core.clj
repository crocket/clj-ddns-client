(ns clj-ddns-client.core
  (:require [clojure.edn :as edn]
            [clojure.core.async :as a]
            [clj-time.core :as t]
            [clj-time.periodic :as p]
            [chime :refer [chime-at chime-ch]]
            [taoensso.timbre :as log]
            [taoensso.timbre.appenders.core :as appenders]
            [taoensso.timbre.appenders.3rd-party.rotor :as rotor]
            [clj-ddns-client.providers.core :as provider]
            [clojure.tools.cli :as cli]
            ;; provider implementations
            clj-ddns-client.providers.dnsever)
  (:gen-class))

(defn- make-provider-updater!
  "Make an alarm channel and a new thread that updates the given provider according to the alarm.
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
  "Returns alarm channels that signal ddns entry updates every :update-interval seconds.
  Close the channels to cancel schedules.
  config = config.edn"
  [config]
  (let [times (p/periodic-seq (t/now)
                              (-> (:update-interval config) t/seconds))
        providers (:providers config)]
    (doall (map (partial make-provider-updater! times)
                providers))))

(def cli-options
  [["-h" "--help"]
   ["-c" "--log-to-console" "Log to console in addition to a log file"]
   ["-f" "--logfile PATH" "Path to log file"]])

(defn- handle-cli-help!
  "Print help if wrong arguments are passed or --help is passed.
  Otherwise, invoke f with command line arguments parsed by clojure.tools.cli"
  [args f]
  (let [cli-args (cli/parse-opts args cli-options)
        errors (:errors cli-args)
        options (:options cli-args)]
    (if (or errors
            (:help options))
      (do
        (doseq [error errors]
          (println error))
        (println (:summary cli-args)))
      (f cli-args))))

(defn- get-default-appenders
  [logfile]
  {:file-appender (rotor/rotor-appender
                   {:path logfile})})

(defn- apply-config!
  [config]
  ;; Configure logger according to cofig.edn
  (log/set-level! (:log-level config))
  (log/swap-config! (fn [c]
                      (assoc c :appenders (get-default-appenders (:log-file config))))))

(defn- apply-cli-options!
  [{:keys [options arguments]}]
  ;; Configure logger according to command line arguments
  (when (:log-to-console options)
    (log/merge-config! {:appenders {:println (appenders/println-appender)}}))
  (when-let [logfile (:logfile options)]
    (log/merge-config! {:appenders (get-default-appenders logfile)})))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (handle-cli-help! args
    (fn [cli-args]
      (let [config (-> "config.edn" slurp edn/read-string)]
        (apply-config! config)
        (apply-cli-options! cli-args)
        ;; start updating DDNS
        (let [update-alarms (start-updating! config)]
          (try
            (.join (Thread/currentThread))
            (finally
              (println "Interrupted")
              (doseq [alarm update-alarms]
                (a/close! alarm)))))))))
