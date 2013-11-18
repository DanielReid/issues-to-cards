(ns issues-to-cards.handler
  (:import com.mchange.v2.c3p0.ComboPooledDataSource)
  (:require [compojure.handler :as handler]
            [compojure.core :refer :all]
            [ring.middleware.json :as middleware]
            [clojure.java.jdbc :as sql]
            [ring.util.response :refer :all]
            [compojure.route :as route]
            [overtone.at-at :as atat]
            [clj-http.client :as client]
            [cheshire.core :refer :all]
            [clojure.pprint :refer :all]))

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

; Global issue/card map until I figure something better out. Better than remaking it.
(def issue-card-map (atom {}))

; TODO: delete crud ?
(def card-url (str trello-base-url "cards" ))
(def key-and-token (str "?key=" trello-key "&token=" trello-token))

; Deprecated until webhooks use
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
(defn- println* [str]  (println str) str)
                                        ; END

(defn github-issue-id [github-info-map] ; example "danielreid/issues-to-cards#1"
  (str 
   (:owner github-info-map) "/" 
   (:repo-name github-info-map) "#" 
   (:issue-number github-info-map)))

(defn github-info-from-url [url]
  (def elements (clojure.string/split url #"/"))
  ; example (assumes html url, not api)
  ; ["https:" "" "github.com" "drugis" "mcda-elicitation-web" "issues" "32"]
  {:owner (elements 3) 
   :repo-name (elements 4)
   :issue-number (elements 6)})

(defn get-github-issues [] 
  (let [issues (client/get (str github-base-url mcda-repo-issues))]
    (prn "github issues retrieved.")
    (decode (:body issues))))

(defn get-trello-cards [] 
  (let [trello-url (str trello-base-url
                       "boards/" addis-dev-board-id
                       "/cards"
                       "?key=" trello-key
                       "&token=" trello-token)]
    (println "trello cards retrieved.")
    (decode (:body (client/get trello-url)))))

(defn github-label-to-trello [github-label]
  (let [label-map 
        {"bug" "red"
         "story" "orange"}]
    (get label-map github-label)))

(defn github-labels-to-trello [labels]
  (let [label-names (map #(get %1 "name") labels)]
    (remove nil? (map github-label-to-trello label-names))))

(defn github-id-from-issue [issue]
  (github-issue-id (github-info-from-url (get issue "html_url"))))

(defn short-github-id-from-issue [issue]
  (second (clojure.string/split (github-id-from-issue issue) #"/")))

(defn trello-card-body [name desc issue list-id]
   (encode 
    {:name name
     :desc desc
     :due nil 
     :labels (github-labels-to-trello (get issue "labels"))
     :idList list-id}))

(defn create-card [issue list-id]
  (let 
      [name (str "[" 
                 (short-github-id-from-issue issue)
                 "] " (get issue "title"))
       desc (str "[" (github-id-from-issue issue) "](" (get issue "html_url") ")\n"
                 (get issue "body"))]
    (println (str "creating " name))
    (swap! issue-card-map assoc issue ; update issue/card map with created card
           (decode (:body                       
                    (client/post 
                     (str card-url key-and-token)
                     {:body (trello-card-body name desc issue list-id)
                      :content-type :json}))))))

(defn create-new-cards 
  "Make a new trello card for each github issue for which there is not yet
  one. Checks for a card with name that conains the github id (without owner)."
  []
  (let 
      [issues-without-cards (remove #(get @issue-card-map %1) (keys @issue-card-map))
       created-cards (map #(create-card %1 addis-dev-todo-list) issues-without-cards)]
     (println (str "created " (count created-cards) " cards."))
     created-cards))

(defn update-card-list [issue card]     
  ; if issue is closed then cardlist -> addis-dev-done-list
  ; if issue is open and assigned then cardlist -> addis-dev-doing-list
  (let [new-list-id
        (if (= (get issue "state") "closed")
          addis-dev-done-list
          (if (get issue "assignee")
            addis-dev-doing-list
            addis-dev-todo-list))
        changed? (not (or (nil? new-list-id) (= new-list-id (get card "idList"))))
        change-map (hash-map :changed changed?
                    :card (if changed? 
                            (assoc card "idList" new-list-id)
                            card)
                    :issue issue)]
    change-map))
        

(defn find-card-matching-issue [issue cards]
  (some 
   #(if (.contains 
              (get %1 "name")
              (short-github-id-from-issue issue))
      %1)
   cards))

(defn associate-issues-with-cards 
  "Create map where issues are keys to their corresponding cards"
  [issues cards]
  (doseq [issue issues]
    (swap! issue-card-map assoc issue (find-card-matching-issue issue cards))))

(defn move-cards []
  (println "moving cards.")
  (let 
      [updated-cards-and-issues (doall (map #(update-card-list (key %1) (val %1)) @issue-card-map))
       changed (filter #(:changed %1) updated-cards-and-issues) ]
    (println (str "moving " (count changed) " cards."))
    (doseq [entry changed]
      (swap! issue-card-map assoc (:issue entry) (:card entry))
      (client/put 
       (str card-url "/" (get (:card entry) "id") "/idList" key-and-token)
       {:body (encode 
               {:value (get (:card entry) "idList")})
        :content-type :json}))))

(defn poll []
  (println "polling...")
  (let 
      [github-issues (get-github-issues)
       trello-cards (get-trello-cards)
       created-cards (create-new-cards)]
    (move-cards)
    (println "done polling.")))

(defn init []
  (let [github-issues (get-github-issues)
        trello-cards (get-trello-cards)]
    (associate-issues-with-cards github-issues trello-cards)
    (doseq [mapentry @issue-card-map] 
      (prn (str (get (key mapentry) "title") " : "
                (get (val mapentry) "name"))))
    (println "init finished.")))

(def my-pool (atat/mk-pool))
(atat/every 30000 poll my-pool :initial-delay 10000)
