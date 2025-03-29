# WordleScore

Reads messages from a specified Slack Channel and saves them to an embedded database. Then sends a weekly report to the same Slack channel with the result from the current week.

## How to run

To use this app you will need to create a Slack App with the correct permissions. The permissions are:
- chat:write
- channels:read
- cahnnels:history

You need to invite this new app to the channel you want to read from.
When your app is created you will need to add these environmental variables before you run your code:
```bash
set SLACK_BOT_TOKEN=xoxb-...
```
This token can be found where you created your Slack App.

Before you can run the application you will need to specify a list of users in a file called *users.json* (See example_users.json for an example)\
You also need to specify some properties in a file called *config.properties* (See example.config.properties for an example)

To run the application use:
```bash
./gradlew run
```