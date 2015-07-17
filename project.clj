(defproject clj-ddns-client "0.1.0"
  :description "It updates DDNS entries of various DDNS providers."
  :url "http://github.com/crocket/clj-ddns-client"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [clj-time "0.10.0"]
                 [jarohen/chime "0.1.6"]
                 [clj-http "1.1.2"]
                 [com.taoensso/timbre "4.0.2"]
                 [org.clojure/tools.cli "0.3.1"]]
  :main ^:skip-aot clj-ddns-client.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
