(ns issues-to-cards.handler
  (:import com.mchange.v2.c3p0.ComboPooledDataSource)
  (:use compojure.core)
  (:use ring.util.response)
  (:use clojure.pprint)
  (:require [compojure.handler :as handler]
            [ring.middleware.json :as middleware]
            [clojure.java.jdbc :as sql]
            [compojure.route :as route]
            [overtone.at-at :as atat]
            [clj-http.client :as client]
            [cheshire.core :refer :all]))

; string constants for access
(def trello-base-url "https://api.trello.com/1/")
(def github-base-url "https://api.github.com/repos/")
(def mcda-repo-issues "drugis/mcda-elicitation-web/issues")

(def trello-key "d75612b792255bae907b8d2bf4db0de6")
(def trello-token "fb1d07abe5006c37de3ff16bce7e0caf2c19233198e961493bb6f4247372c364")
(def addis-dev-board-id "1h6bSddJ")
(def addis-board-id "tfTOMeXe")

(def addis-dev-todo-list "5270ca8844d20c90510038c1")
(def addis-dev-doing-list "5270ca8844d20c90510038c2")
(def addis-dev-done-list "5270ca8844d20c90510038c3")

; TODO: delete crud ?
(def card-url (str trello-base-url "cards" ))
(def key-and-token (str "?key=" trello-key "&token=" trello-token))

(defroutes app-routes
  (context "/" []
           (defroutes root-routes
             (GET "/" [] (fn [a] "Hello World"))
             (POST "/" {body :body} (fn [a] (println a)))
             (context "/:id" [id] 
                      (defroutes id-routes
                        (GET "/" [] (fn [id] id))))))
  (context "/test" []
           (defroutes test-routes
             (GET "/" [] (fn [a] "HELLO OTHER WORLD"))))
  (route/not-found "Not Found"))

(def app
  (-> (handler/api app-routes)
      (middleware/wrap-json-body)
      (middleware/wrap-json-response)))

; TODO delete (debug)
(defn print-mapentries [header-string key entries]
    (doall (map #(println (str header-string (get %1 key))) entries)))
(defn- println* [str]
  (println str)
  str)
; END

(defn github-issue-id [github-info-map]
  ; example "danielreid/issues-to-cards#1"
  (str 
   (:owner github-info-map) "/" (:repo-name github-info-map) "#" (:issue-number github-info-map)))

(defn github-info-from-url [url]
  (def elements (clojure.string/split url #"/"))
  ; example (assumes html url, not api)
  ; ["https:" "" "github.com" "drugis" "mcda-elicitation-web" "issues" "32"]
  {:owner (elements 3) 
   :repo-name (elements 4)
   :issue-number (elements 6)})

(defn get-github-issues [] 
  (def issues (client/get (str github-base-url mcda-repo-issues)))
  (decode (:body issues)))

(defn get-trello-cards [] 
  (def trello-url (str trello-base-url
                       "boards/" addis-dev-board-id
                       "/cards"
                       "?key=" trello-key
                       "&token=" trello-token))
  (decode (:body (client/get trello-url))))

(defn github-label-to-trello [github-label]
  (get {"bug" "red"
   "story" "orange"} github-label))

(defn github-labels-to-trello [labels]
  (def label-names (map #(get %1 "name") labels))
  (remove nil? (map github-label-to-trello label-names))
)

(defn create-card [issue list-id]
  (def name (github-issue-id (github-info-from-url (get issue "html_url"))))
  (def desc (str "[" name "](" (get issue "html_url") ")\n"
                 (get issue "body")))
  (client/post (str card-url key-and-token)
               {:body
                (encode 
                 {:name name
                  :desc desc
                  :due nil 
                  :labels (github-labels-to-trello (get issue "labels"))
                  :idList list-id})
                :content-type :json}))

(defn check-and-create-new-cards [github-issues trello-cards]
  (def issues-without-cards (filter 
                             (fn [github-entry] 
                               (not (some 
                                (fn [trello-entry] 
                                  (if (= (get trello-entry "name")
                                         (get github-entry "title"))
                                    trello-entry))
                                trello-cards)))
                             github-issues))
  (doseq [issue issues-without-cards]
    (create-card issue addis-dev-todo-list))
)

(defn poll []
  (println "polling...")
  (def github-issues (get-github-issues))
  (def trello-cards (get-trello-cards))
  (check-and-create-new-cards github-issues trello-cards)
  (println "done polling.")
)

(defn init []
 (client/post (str card-url "/" "52711cc35449cac13a0052a4" "/actions/comments" key-and-token)
               {:body
                (encode {:text "testcomment"})
                :content-type :json})
  (println "-------sync initialised------")
  
)

(def my-pool (atat/mk-pool))
(atat/every 30000 poll my-pool)
