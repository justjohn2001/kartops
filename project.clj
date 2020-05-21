(defproject kartops "0.1.0-SNAPSHOT"
  :description "Demonstration of how to parse Mario Kart Wii packets"
  :license {:name "3-Clause BSD License"
            :url "https://opensource.org/licenses/BSD-3-Clause"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.pcap4j/pcap4j-core "1.7.3"]
                 [org.slf4j/slf4j-simple "1.7.5"]]
  :main kartops.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
