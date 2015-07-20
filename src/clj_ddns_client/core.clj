(ns clj-ddns-client.core
  (:require [clojure.edn :as edn]
            [clojure.core.async :as a]
            [clj-time.core :as t]
            [clj-time.periodic :as p]
            [chime :refer [chime-at chime-ch]]
            [unilog.config :refer [start-logging!]]
            [clojure.tools.logging :as log]
            [clj-ddns-client.provider :as provider]
            [clojure.tools.cli :as cli]
            ;; It works around :file-rolling appender issues in unilog 0.7.5.
            [clj-ddns-client.unilog-fix :as unilog-fix]
            ;; provider implementations
            clj-ddns-client.providers.dnsever)
  (:gen-class))

(def cli-options
  [["-h" "--help"]
   ["-v" "--verbose" "Log to console in addition to a log file"]
   ["-f" "--logfile PATH" "Path to log file"]
   ["-c" "--config PATH" "Path to config file"
    :default "config.edn"]])

(def default-log-config
  {:level :info
   :console false
   :appenders [{:appender :rolling-file
                :rolling-policy {:type :fixed-window
                                 :max-index 5}
                :triggering-policy {:type :size-based
                                    ;; 51200 bytes = 50KBytes
                                    :max-size 51200}
                :file "clj-ddns-client.log"}]})

(defn- launch-updater!
  "It launches a new thread that updates provider according to schedule.
  If a provider update throws an exception, the channel is closed.
  The thread is automatically closed when the channel is closed.
  provider = One of :providers in config.edn"
  [schedule provider]
  (a/go-loop []
    (when-let [time (a/<! schedule)]
      (try
        (provider/update! provider)
        (catch Exception e
          (log/error e)
          (a/close! schedule)))
      (recur))))

(defn- start-updaters!
  "It launches new threads that update providers accoring to schedule.
  It returns [{:updater 'channel that emits when the associated thread exits'
               :schedule 'channel that signals schedules'} ...].
  Close :schedule to cancel schedules and kill its associated updater thread."
  [config]
  (let [times (p/periodic-seq (t/now)
                              (-> (:update-interval config) t/seconds))]
    (doall (map (fn [provider]
                  (let [channel (-> 1 a/sliding-buffer a/chan)
                        schedule (chime-ch times {:ch channel})]
                    {:updater (launch-updater! schedule provider)
                     :schedule schedule}))
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

(defn- assoc-in-cond
  "Associate a val to a nested key in a map when the val evaluates to true.
  You can specify multiple keys-val pairs."
  [m & keys-vals]
  (reduce (fn [m [keys val]] (if val (assoc-in m keys val) m))
          m
          (partition 2 keys-vals)))

(defn- apply-config-to-logger
  "Add logger options according to cofig.edn"
  [log-config {:keys
               [log-level log-file log-file-size log-file-count overrides]}]
  (assoc-in-cond log-config
                 [:level] log-level
                 [:appenders 0 :file] log-file
                 [:appenders 0 :triggering-policy :max-size] log-file-size
                 [:appenders 0 :rolling-policy :max-index] log-file-count
                 [:overrides] (reduce-kv #(assoc %1 %2 (name %3)) {} overrides)))

(defn- apply-cli-options-to-logger
  "Add logger options according to command line arguments"
  [log-config {{:keys [verbose logfile]} :options arguments :arguments}]
  (assoc-in-cond log-config
                 [:console] verbose
                 [:appenders 0 :file] logfile))

(defn -main
  "Start DDNS client"
  [& args]
  (handle-cli-help!
   args
   (fn [cli-args]
     (let [config (-> cli-args :options :config slurp edn/read-string)]
       ;; Construct log config and apply it to logger
       (-> default-log-config
           (apply-config-to-logger config)
           (apply-cli-options-to-logger cli-args)
           unilog-fix/start-logging!)
       ;; Start DDNS provider updaters
       (let [updaters (start-updaters! config)]
         (try
           ;; Quit the program if any provider updater stops.
           (a/alts!! (into [] (map :updater updaters)))
           (finally ; This is executed in REPL to clean up updaters.
             (println "Finished")
             (doseq [schedule (map :schedule updaters)]
               ;; Closing schedule kills its associated updater.
               (a/close! schedule)))))))))
