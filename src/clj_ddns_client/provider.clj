(ns clj-ddns-client.provider)

(defmulti update!
  "config = One of :providers in config.edn"
  :provider
  :default nil)

(defmethod update! nil
  [config]
  (throw (Exception. (format "Provider '%s' doesn't exist."
                             (:provider config)))))
