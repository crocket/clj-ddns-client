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
            ;; provider implementations
            clj-ddns-client.providers.dnsever)
  (:import java.io.IOException)
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
                                    ;; 1048576 bytes = 1 MegaBytes
                                    :max-size 1048576}
                :file "clj-ddns-client.log"
                :encoder :pattern
                :pattern "%p [%d] %t - %c%n%m%n"}]})

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
          (log/error e)))
      (recur))))

(defn- start-updaters!
  "It launches new threads that update providers accoring to schedule.
  It returns [{:updater 'channel that emits when the associated thread exits'
               :schedule 'channel that signals schedules'} ...].
  Close :schedule to cancel schedules and kill its associated updater thread."
  [{:keys [providers update-interval] :as config}]
  (let [times (p/periodic-seq (t/now)
                              (-> update-interval t/seconds))]
    (doall (map (fn [provider]
                  (let [channel (-> 1 a/sliding-buffer a/chan)
                        schedule (chime-ch times {:ch channel})]
                    {:updater (launch-updater! schedule provider)
                     :schedule schedule}))
                providers))))

(defn- handle-cli-help!
  "Print help if wrong arguments are passed or --help is passed.
  Otherwise, invoke f with command line arguments parsed by clojure.tools.cli"
  [args f]
  (let [cli-args (cli/parse-opts args cli-options)
        errors (:errors cli-args)]
    (if (or errors
            (-> cli-args :options :help))
      (do
        (run! println errors)
        (println (:summary cli-args)))
      (f cli-args))))

(defn- assoc-in-cond
  "Associate a val to a nested key in a map when the val evaluates to true.
  You can specify multiple keys-val pairs."
  [m & keys-vals]
  (transduce (comp (partition-all 2) (filter second))
             (completing (fn [m [keys val]] (assoc-in m keys val)))
             m keys-vals))

(defn- apply-config-to-logger
  "Add logger options according to cofig.edn"
  [log-config {:keys
               [log-level log-file log-file-size log-file-count overrides]}]
  (assoc-in-cond log-config
                 [:level] log-level
                 [:appenders 0 :file] log-file
                 [:appenders 0 :triggering-policy :max-size] log-file-size
                 [:appenders 0 :rolling-policy :max-index] log-file-count
                 [:overrides] (into {} (map #(update % 1 name) overrides))))

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
           start-logging!)
       ;; Start DDNS provider updaters
       (let [updaters (start-updaters! config)]
         (try
           ;; Quit the program if any provider updater stops.
           (a/alts!! (map :updater updaters))
           (finally ; This is executed in REPL to clean up updaters.
             (println "Finished")
             ;; Closing schedule kills its associated updater.
             (run! a/close! (map :schedule updaters)))))))))
