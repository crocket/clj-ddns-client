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

(def cli-options
  [["-h" "--help"]
   ["-v" "--verbose" "Log to console in addition to a log file"]
   ["-f" "--logfile PATH" "Path to log file"]
   ["-c" "--config PATH" "Path to config file"
    :default "config.edn"]])

(defn- launch-provider-updater!
  "It launches a new thread that updates the given provider according to chime signals.
  If a provider update throws an exception, the channel is closed.
  The thread is automatically closed when the channel is closed.
  provider = One of :providers in config.edn"
  [chimes provider]
  (a/go-loop []
    (when-let [time (a/<! chimes)]
      (try
        (provider/update! provider)
        (catch Exception e
          (log/error e)
          (a/close! chimes)))
      (recur))))

(defn- start-updating!
  "It launches new threads that update providers accoring to schedule.
  It returns [{:updater-ch 'a channel that emits a value when it exits'
               :chimes 'a channel that signals schedules'} ...].
  Close the :chimes to cancel schedules and kill its associated updater thread."
  [config]
  (let [times (p/periodic-seq (t/now)
                              (-> (:update-interval config) t/seconds))]
    (doall (map (fn [provider]
                  (let [channel (-> 1 a/sliding-buffer a/chan)
                        chimes (chime-ch times {:ch channel})]
                    {:updater-ch (launch-provider-updater! chimes provider)
                     :chimes chimes}))
                (:providers config)))))

(defn- handle-cli-help!
  "Print help if wrong arguments are passed or --help is passed.
  Otherwise, invoke f with command line arguments parsed by clojure.tools.cli"
  [args f]
  (let [cli-args (cli/parse-opts args cli-options)
        errors (:errors cli-args)]
    (if (or errors
            (-> cli-args :options :help))
      (do
        (doseq [error errors]
          (println error))
        (println (:summary cli-args)))
      (f cli-args))))

(defn- assoc-in-cond3
  "Associate val in a nested map structure when cond is true.
  You can specify multiple cond-keys-val triplets."
  [m & cond-keys-vals]
  (reduce (fn [m [cond keys val]] (if cond (assoc-in m keys val) m))
          m
          (partition 3 cond-keys-vals)))

(defn- assoc-in-cond
  "Associate a val to a nested key in a map when the key is true.
  You can specify multiple keys-val pairs."
  [m & keys-vals]
  (reduce (fn [m [keys val]] (if val (assoc-in m keys val) m))
          m
          (partition 2 keys-vals)))

(defn- apply-config-to-logger
  "Add logger options according to cofig.edn"
  [{:keys [log-level log-file log-file-size log-file-count]} log-config]
  (assoc-in-cond log-config
                 [:level] log-level
                 [:appenders :file-appender :arg-map :path] log-file
                 [:appenders :file-appender :arg-map :max-size] log-file-size
                 [:appenders :file-appender :arg-map :backlog] log-file-count))

(defn- apply-cli-options-to-logger
  "Add logger options according to command line arguments"
  [{{:keys [verbose logfile]} :options arguments :arguments} log-config]
  (assoc-in-cond3 log-config
                  verbose [:appenders :println :fn] appenders/println-appender
                  logfile [:appenders :file-appender :arg-map :path] logfile))

(defn- turn-off-ansi-colors-in
  [appender-id log-config]
  (assoc-in log-config [:appenders appender-id :output-fn]
            (partial log/default-output-fn {:stacktrace-fonts {}})))

(defn- reify-appenders
  "Convert {:appenders {:appender-id {:fn fn :arg-map arg-map}}} to
  {:appenders {:appender-id (fn arg-map)}}"
  [log-config]
  (update-in log-config [:appenders] #(reduce-kv (fn [m k {:keys [fn arg-map]}]
                                                   (assoc m k (fn arg-map)))
                                                 {} %)))

(defn- apply-log-config!
  [log-config]
  ;; override appenders
  (log/swap-config! #(assoc % :appenders (:appenders log-config)))
  ;; merge the rest
  (log/merge-config! (dissoc log-config :appenders)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (handle-cli-help! args
    (fn [cli-args]
      (let [config (-> cli-args :options :config slurp edn/read-string)]
        (->> {:appenders {:file-appender {:fn rotor/rotor-appender}}}
             (apply-config-to-logger config)
             (apply-cli-options-to-logger cli-args)
             reify-appenders
             (turn-off-ansi-colors-in :file-appender)
             apply-log-config!)
        ;; start updating DDNS
        (let [update-alarms (start-updating! config)]
          (try
            ;; Quit the program if any provider updater stops.
            (a/alts!! (into [] (map :updater-ch update-alarms)))
            (finally ; This is executed in REPL to clean up updaters.
              (println "Finished")
              (doseq [alarm (map :chimes update-alarms)]
                (a/close! alarm))))))))) ; closing chimes kills updaters.
