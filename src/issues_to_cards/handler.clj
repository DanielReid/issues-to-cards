(ns issues-to-cards.handler
  (:gen-class)
  (:require [overtone.at-at :as atat]
            [clj-http.client :as client]
            [clojure.tools.logging :as log]
            [cheshire.core :refer :all]
            [clojure.pprint :refer :all]
            [environ.core :refer :all]))

;; environment variables/string constants
;; github
(def github-base-url "https://api.github.com/repos/")
(def repo-issues (env :repo-issues))
(def github-token (env :github-token))

;; trello
(def trello-base-url "https://api.trello.com/1/")
(def trello-board-id (env :trello-board-id))
(def trello-key (env :trello-key))
(def trello-token (env :trello-token))
(def todo-list (env :todo-list))
(def doing-list (env :doing-list))
(def done-list (env :done-list))

;; connection between labels on github/trello
(def label-map (env :label-map))

;; at-at polling
(def polling-interval (env :polling-interval))
(def initial-poll-delay (env :initial-poll-delay))

;; Global issue/card map until I figure something better out. Better than remaking it.
(def issue-card-map (atom {}))

;; readability defs
(def card-url (str trello-base-url "cards" ))
(def key-and-token (str "?key=" trello-key "&token=" trello-token))
(def all-trello-cards-from-board-url
  (str trello-base-url
       "boards/" trello-board-id
       "/cards"
       key-and-token))

(def archived-trello-cards-from-board-url
  (str trello-base-url
       "boards/" trello-board-id
       "/cards/closed"
       key-and-token))

(defn github-issue-id [github-info-map] ; example "danielreid/issues-to-cards#1"
  (str
   (:owner github-info-map) "/"
   (:repo-name github-info-map) "#"
   (:issue-number github-info-map)))

(defn github-info-from-url [url]
  "example (assumes html url, not api)
   ['https:' '' 'github.com' 'drugis' 'mcda-elicitation-web' 'issues' '32']"
  (let [elements (clojure.string/split url #"/")]
    {:owner (elements 3)
     :repo-name (elements 4)
     :issue-number (elements 6)}))

(defn get-github-issues [state]
  (let [issues (client/get (str github-base-url repo-issues "?state=" state)
                           {:oauth-token github-token})]
    (log/debug state "github issues retrieved.")
    (decode (:body issues))))

(defn get-open-github-issues []
  (get-github-issues "open"))

(defn get-closed-github-issues []
  (get-github-issues "closed"))

(defn get-trello-cards []
  (log/debug "open trello cards retrieved.")
  (decode (:body (client/get all-trello-cards-from-board-url))))

(defn get-archived-trello-cards []
  (log/debug "archived trello cards retrieved.")
  (decode (:body (client/get archived-trello-cards-from-board-url))))

(defn github-label-to-trello [github-label]
  get label-map github-label)

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

(defn truncate-description [desc]
  (if ; Trello max length for description is 16384
      (> 16084 (.length desc))
    desc
    (str "**NB : too-long description truncated**\n" (subs desc 0 16084))))

(defn create-card [issue list-id]
  (let
      [name (str "["
                 (short-github-id-from-issue issue)
                 "] " (get issue "title"))
       desc (str "[" (github-id-from-issue issue) "](" (get issue "html_url") ")\n"
                 (truncate-description (get issue "body")))]
    (log/debug "creating" name)
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
  (log/debug "creating cards")
  (let
      [issues-without-cards (remove #(get @issue-card-map %1) (keys @issue-card-map))
       created-cards (map #(create-card %1 todo-list) issues-without-cards)]
    (println (str "created " (count created-cards) " cards."))
    created-cards))

(defn update-card-list [issue card]
  "if issue is closed then cardlist -> addis-dev-done-list ; if issue
   is open and assigned then cardlist -> doing-list ; nb: if issue is
   dragged to 'doing' on trello do not place back in todo."
  (let [new-list-id
        (if (= (get issue "state") "closed")
          done-list
          (if (get issue "assignee")
            doing-list))
        changed? (and
                  (not (nil? new-list-id))
                  (not (= new-list-id (get card "idList"))))
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
         (str "[" (short-github-id-from-issue issue) "]"))
      %1)
   cards))

(defn associate-issues-with-cards
  "Create map where issues are keys to their corresponding cards"
  [issues cards]
  (doseq [issue issues]
    (swap! issue-card-map assoc issue (find-card-matching-issue issue cards))))

(defn move-cards []
  (log/debug "moving cards")
  (let
      [updated-cards-and-issues (map #(update-card-list (key %1) (val %1)) @issue-card-map)
       changed (filter #(:changed %1) updated-cards-and-issues) ]
    (log/debug "moving" (count changed) "cards.")
    (doseq [entry changed]
      (swap! issue-card-map assoc (:issue entry) (:card entry))
      (client/put
       (str card-url "/" (get (:card entry) "id") "/idList" key-and-token)
       {:body (encode
               {:value (get (:card entry) "idList")})
        :content-type :json}))))

(defn poll []
  (log/debug "polling...")
  (let
      [open-github-issues (get-open-github-issues)
       closed-github-issues (get-closed-github-issues)
       open-trello-cards (get-trello-cards)
       archived-trello-cards (get-archived-trello-cards)
       created-cards (create-new-cards)]
    (associate-issues-with-cards (concat closed-github-issues open-github-issues)
                                 (concat created-cards archived-trello-cards open-trello-cards))
    (move-cards)
    (log/debug "done polling")))


(def my-pool (atat/mk-pool))
(atat/every polling-interval poll my-pool :initial-delay initial-poll-delay)

(defn -main [& args]
  (let
      [open-github-issues (get-open-github-issues)
       closed-github-issues (get-closed-github-issues)
       open-trello-cards (get-trello-cards)
       archived-trello-cards (get-archived-trello-cards)]
    (associate-issues-with-cards (concat closed-github-issues open-github-issues)
                                 (concat archived-trello-cards open-trello-cards))
    (log/debug "init finished.")
    (while (not (.. Thread currentThread isInterrupted))
      (Thread/sleep 100))))
