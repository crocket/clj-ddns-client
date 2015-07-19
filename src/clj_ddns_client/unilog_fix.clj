(ns clj-ddns-client.unilog-fix
  (:require unilog.config)
  (:import ch.qos.logback.core.rolling.RollingFileAppender
           ch.qos.logback.core.rolling.FixedWindowRollingPolicy))

(defmethod unilog.config/build-appender :rolling-file
  [{:keys [rolling-policy triggering-policy file]
    :or {rolling-policy    :fixed-window
         triggering-policy :size-based}
    :as config}]
  (let [appender (RollingFileAppender.)]
    (assoc config :appender (doto appender
                              (.setFile file)
                              (.setRollingPolicy
                               (unilog.config/build-rolling-policy
                                (merge
                                 {:file file}
                                 (cond
                                   (keyword? rolling-policy)
                                   {:type rolling-policy}

                                   (string? rolling-policy)
                                   {:type (keyword rolling-policy)}

                                   (map? rolling-policy)
                                   (update-in rolling-policy [:type] keyword)

                                   :else
                                   (throw (ex-info "invalid rolling policy"
                                                   {:config rolling-policy}))))
                                appender))
                              (.setTriggeringPolicy
                               (unilog.config/build-triggering-policy
                                (merge {:file file}
                                       (cond
                                         (keyword? triggering-policy)
                                         {:type triggering-policy}

                                         (string? triggering-policy)
                                         {:type (keyword triggering-policy)}

                                         (map? triggering-policy)
                                         (update-in triggering-policy [:type] keyword)

                                         :else
                                         (throw
                                          (ex-info "invalid triggering policy"
                                                   {:config triggering-policy}))))))))))

(defmethod unilog.config/build-rolling-policy :fixed-window
  [{:keys [file pattern max-index min-index]
    :or {max-index 5
         min-index 1
         pattern "%d{yyyy-MM-dd}.%i.gz"}}
   parent]
  (doto (FixedWindowRollingPolicy.)
    (.setFileNamePattern (str file pattern))
    (.setMinIndex (int min-index))
    (.setMaxIndex (int max-index))
    (.setParent parent)))
