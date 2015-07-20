(ns clj-ddns-client.providers.dnsever
  (:require [clojure.string :as s]
            [clj-http.client :as client]
            [clj-ddns-client.provider :as provider]
            [clojure.tools.logging :as log]))

(defn- get-domains
  [config]
  (let [domain (:domain config)]
    (map #(str % (when-not (= "" %) ".") domain)
         (:subdomains config))))

(defn- dnsever-ddns-update-url
  [domains]
  (->> domains
       (map #(str "host[" % "]"))
       (s/join "&")
       (str "http://dyna.dnsever.com/update.php?")))

(defmethod provider/update! :dnsever
  [config]
  (let [result (-> config
                   get-domains
                   dnsever-ddns-update-url
                   (client/get {:basic-auth [(:user config)
                                             (:authcode config)]}))]
    (log/infof "\nHTTP Status = %s\n%s"
               (:status result)
               (s/trim (:body result)))))
