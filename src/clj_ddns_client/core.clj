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
   ["-v" "--verbose" "Log to console in addition to a log file"]
   ["-f" "--logfile PATH" "Path to log file"]
   ["-c" "--config PATH" "Path to config file"
    :default "config.edn"]])

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

(defn- apply-config-to-logger
  "Add logger options according to cofig.edn"
  [{:keys [log-level log-file log-file-size log-file-count]} log-config]
  (cond-> log-config
    log-level (assoc :level log-level)
    log-file (assoc-in [:appenders :file-appender :arg-map :path] log-file)
    log-file-size (assoc-in [:appenders :file-appender :arg-map :max-size] log-file-size)
    log-file-count (assoc-in [:appenders :file-appender :arg-map :backlog] log-file-count)))

(defn- apply-cli-options-to-logger
  "Add logger options according to command line arguments"
  [{{:keys [verbose logfile]} :options arguments :arguments} log-config]
  (cond-> log-config
    verbose (assoc-in [:appenders :println :fn] appenders/println-appender)
    logfile (assoc-in [:appenders :file-appender :arg-map :path] logfile)))

(defn- reify-appenders
  "Convert {:appenders {:appender-id {:fn fn :arg-map arg-map}}} to
  {:appenders {:appender-id (fn arg-map)}}"
  [log-config]
  (reduce (fn [log-config appender-id]
            (update-in log-config [:appenders appender-id] (fn [{:keys [fn arg-map]}]
                                                             (fn arg-map))))
          log-config
          (keys (:appenders log-config))))

(defn- apply-log-config!
  [log-config]
  ;; override appenders
  (log/swap-config! (fn [c]
                      (assoc c :appenders (:appenders log-config))))
  ;; merge the rest
  (log/merge-config! (dissoc log-config :appenders)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (handle-cli-help! args
    (fn [cli-args]
      (let [config (-> cli-args :options :config slurp edn/read-string)
            log-config {:appenders {:file-appender {:fn rotor/rotor-appender}}}]
        (->> log-config
             (apply-config-to-logger config)
             (apply-cli-options-to-logger cli-args)
             reify-appenders
             apply-log-config!)
        ;; start updating DDNS
        (let [update-alarms (start-updating! config)]
          (try
            (.join (Thread/currentThread))
            (finally
              (println "Interrupted")
              (doseq [alarm update-alarms]
                (a/close! alarm)))))))))
