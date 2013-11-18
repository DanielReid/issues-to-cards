(defproject issues-to-cards "0.1.0-SNAPSHOT"
  :description "One-way synchronisation from Github issues to Trello cards (including creation, moving and archiving)"
  :url "https://github.com/DanielReid/issues-to-cards"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [compojure "1.1.5"]
                 [ring/ring-json "0.1.2"]
                 [com.cemerick/pomegranate "0.2.0"]
                 [c3p0/c3p0 "0.9.1.2"]
                 [org.clojure/java.jdbc "0.2.3"]
                 [com.h2database/h2 "1.3.168"]
                 [clj-http "0.7.7"]
                 [cheshire "5.2.0"]
                 [overtone/at-at "1.2.0"]
                 [environ "0.4.0"]]
  :plugins [[lein-ring "0.8.7"]
            [lein-environ "0.4.0"]]
  :ring {:handler issues-to-cards.handler/app
         :init issues-to-cards.handler/init}
  :profiles
  {:dev {:dependencies [[ring-mock "0.1.5"]]}})
