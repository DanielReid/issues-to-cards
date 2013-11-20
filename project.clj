(defproject issues-to-cards "0.1.0-SNAPSHOT"
  :description "One-way synchronisation from Github issues to Trello cards (including creation, moving and archiving)"
  :url "https://github.com/DanielReid/issues-to-cards"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.logging "0.2.6"]
                 [log4j "1.2.17" :exclusions [javax.mail/mail
                                              javax.jms/jms
                                              com.sun.jdmk/jmxtools
                                              com.sun.jmx/jmxri]]
                 [cheshire "5.2.0"]
                 [clj-http "0.7.7"]
                 [overtone/at-at "1.2.0"]
                 [environ "0.4.0"]]
  :main issues-to-cards.handler
  :plugins [[lein-environ "0.4.0"]])
