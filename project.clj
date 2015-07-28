(defproject clj-ddns-client "1.3.2"
  :description "It updates DDNS entries of various DDNS providers."
  :url "http://github.com/crocket/clj-ddns-client"
  :license {:name "Mozilla Public License 2.0"
            :url "http://www.mozilla.org/MPL/2.0/"
            :year 2015
            :key "mpl-2.0"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [clj-time "0.10.0"]
                 [jarohen/chime "0.1.6"]
                 [clj-http "1.1.2"]
                 ;; logging
                 [spootnik/unilog "0.7.6"]
                 [org.clojure/tools.logging "0.3.1"]
                 ;; cli
                 [org.clojure/tools.cli "0.3.1"]]
  :main ^:skip-aot clj-ddns-client.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
