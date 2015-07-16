(ns clj-ddns-client.providers.dnsever
  (:require [clojure.string :as s]
            [clj-http.client :as client]
            [clj-ddns-client.providers.core :as provider]))

(defn- fully-qualified-subdomains
  [domain subdomains]
  "Combine domain with subdomains to acquire fully qualified domains"
  (map (fn [subdomain]
         (if (= subdomain "")
           domain
           (str subdomain "." domain)))
       subdomains))

(defn- get-domains
  [config]
  (fully-qualified-subdomains (:domain config) (:subdomains config)))

(defn- dnsever-ddns-update-url
  [domains]
  (->> domains
       (map #(str "host[" % "]"))
       (s/join "&")
       (str "http://dyna.dnsever.com/update.php?")))

(defmethod provider/update! :dnsever
  [config]
  (-> config
      get-domains
      dnsever-ddns-update-url
      (client/get {:basic-auth [(:user config) (:authcode config)]})
      (#(provider/log! config
                       (format "HTTP Status = %s\n%s"
                               (:status %)
                               (s/trim (:body %)))
                       :debug))))
