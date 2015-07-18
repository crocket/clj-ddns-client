(defproject clj-ddns-client "1.2.1-SNAPSHOT"
  :description "It updates DDNS entries of various DDNS providers."
  :url "http://github.com/crocket/clj-ddns-client"
  :license {:name "GNU Lesser General Public License v3.0"
            :url "http://www.gnu.org/licenses/lgpl-3.0.txt"
            :year 2015
            :key "lgpl-3.0"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [clj-time "0.10.0"]
                 [jarohen/chime "0.1.6"]
                 [clj-http "1.1.2"]
                 [com.taoensso/timbre "4.0.2"]
                 [org.clojure/tools.cli "0.3.1"]]
  :main ^:skip-aot clj-ddns-client.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
