# issues-to-cards

issues-to-cards is a small project with the goal of one-way synchronisation from Github issues to cards on a Trello board. The idea is to use the strengths of both (easy iteration planning on Trello, tightly integrated commits/comments/issues on Github) with a minimum of fuss.

issues-to-cards is a polling service so that it will also work for projects that are already underway. Additional webhooks listening may be added later for example for comment/commit tracking.

Functionality in short:
* retrieve all issues from github issues
* search all trello-cards for cards that have the github issue ID in the title
* for each issue without a corresponding card, create a new card in the to-do-list.
* each issue that is assigned to a user is assumed to be in progress and is moved to the doing-list.
* each issue that is closed is assumed to be done, and is moved to the done-list.
* archived cards are ignored.

## Prerequisites

You will need [Leiningen][1] 1.7.0 or above installed.

[1]: https://github.com/technomancy/leiningen

## Configuration

issues-to-cards is configured via the environment. Variables such as the ids of the lists on the Trello board, the location of the issues can be supplied either via Leiningen profiles, the system environment or Java system properties. See [environ](https://github.com/weavejester/environ) for details.

Items that need to be configured:
* the location of your issues, e.g.: "danielreid/issues-to-cards/issues"
* the id of the Trello board (get this from the URL of the board)
* a security key and token for your Trello board so that the server can modify your board.
* the ids of the to-do, doing and done lists on your Trello board
* a security token from github

Example configuration, in ~/.lein/profiles.clj:
        {:user 
         {:env 
           {:github-token "<yourtoken>"
            :trello-key "<yourkey>"
            :trello-token "<yourtoken>"
            :repo-issues "danielreid/issues-to-cards/issues"
            :trello-board-id "<yourboardid>"
            :todo-list  "<yourlistid1>"
            :doing-list "<yourlistid2>"
            :done-list  "<yourlistid3>"}}}

### Trello configuration

First, generate your developer key [here](https://trello.com/1/appKey/generate).
Then, go to the following URL (replace 'yourkeyhere' with the Key supplied by Trello). 
https://trello.com/1/authorize?key=yourkeyhere=issues-to-cards&expiration=never&response_type=token&scope=read,write

Trello will ask you to confirm access for the server to your account. On clicking 'Allow' you will receive a token.

To find the ids of the lists on your board, go to the following URL, substituting your boardID, key and token:
https://api.trello.com/1/boards/yourboardhere/lists?key=yourkeyhere&token=yourtokenhere

###Github authentication

Go [here](https://github.com/settings/applications) and create a new token for issues-to-cards.

## Running

To start a web server for the application, run:

    lein ring server

## License

Copyright © 2013 FIXME
