(ns clj-ddns-client.providers.core
  (:require [taoensso.timbre :as log]))

(defmulti update!
  "config = One of :providers in config.edn"
  (fn [config] (:provider config))
  :default nil)

(defmethod update! nil
  [config]
  (throw (Exception. (format "Provider '%s' doesn't exist."
                             (:provider config)))))

(defn log!
  "Record a log. Default log level is :info.
  Refer to config.sample.edn for available log levels."
  ([config log-string log-level]
   (log/log log-level (format "DDNS Provider : %s\n%s"
                              (name (:provider config))
                              log-string)))
  ([config log-string]
   (log! config log-string :info)))
