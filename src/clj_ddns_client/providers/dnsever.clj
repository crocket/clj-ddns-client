(ns clj-ddns-client.providers.dnsever
  (:require [clojure.string :as s]
            [clj-http.client :as client]
            [clj-ddns-client.provider :as provider]
            [clojure.tools.logging :as log]))

(defn- get-domains
  [{:keys [domain subdomains] :as config}]
  (map #(str % (when-not (= "" %) ".") domain)
       subdomains))

(defn- dnsever-ddns-update-url
  [domains]
  (->> domains
       (map #(str "host[" % "]"))
       (s/join "&")
       (str "http://dyna.dnsever.com/update.php?")))

(defmethod provider/update! :dnsever
  [{:keys [user authcode] :as config}]
  (let [result (-> config
                   get-domains
                   dnsever-ddns-update-url
                   (client/get {:basic-auth [user authcode]}))]
    (log/infof "HTTP Status = %s\n%s"
               (:status result)
               (s/trim (:body result)))))
