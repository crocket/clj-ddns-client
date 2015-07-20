(ns clj-ddns-client.unilog-fix
  (:require unilog.config)
  (:import ch.qos.logback.core.rolling.RollingFileAppender
           ch.qos.logback.core.rolling.FixedWindowRollingPolicy
           org.slf4j.LoggerFactory
           ch.qos.logback.classic.Logger
           ch.qos.logback.classic.Level))

(defmethod unilog.config/build-appender :rolling-file
  [{:keys [rolling-policy triggering-policy file context]
    :or {rolling-policy    :fixed-window
         triggering-policy :size-based}
    :as config}]
  (let [appender (RollingFileAppender.)]
    (assoc config :appender (doto appender
                              (.setFile file)
                              (.setContext context)
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
                                                   {:config rolling-policy})))
                                 {:parent appender})))
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
  [{:keys [file pattern max-index min-index parent]
    :or {max-index 5
         min-index 1
         pattern ".%i.gz"}}]
  (doto (FixedWindowRollingPolicy.)
    (.setFileNamePattern (str file pattern))
    (.setMinIndex (int min-index))
    (.setMaxIndex (int max-index))
    (.setParent parent)
    (.setContext (.getContext parent))
    (.start)))

(defn start-logging!
  ([{:keys [external level overrides] :as config}]
   (when-not external
     (let [level   (get unilog.config/levels (some-> level name) Level/INFO)
           root    (LoggerFactory/getLogger Logger/ROOT_LOGGER_NAME)
           context (LoggerFactory/getILoggerFactory)
           assoc-context (fn [f] (comp f #(assoc % :context context)))
           configs (->> (merge {:console true} config)
                        (map unilog.config/appender-config)
                        (flatten)
                        (remove nil?)
                        (map (assoc-context unilog.config/build-appender))
                        (map unilog.config/build-encoder))]

       (.detachAndStopAllAppenders root)

       (doseq [{:keys [encoder appender]} configs]
         (when encoder
           (.setContext encoder context)
           (.start encoder))
         (let [appender (if (fn? appender)
                          (appender encoder context)
                          (doto appender
                            (.setEncoder encoder)
                            (.setContext context)))]
           (.start appender)
           (.addAppender root appender)))

       (.setLevel root level)
       (doseq [[logger level] overrides
               :let [logger (LoggerFactory/getLogger (name logger))
                     level  (get unilog.config/levels level Level/INFO)]]
         (.setLevel logger level))
       root)))
  ([]
   (start-logging! {})))
