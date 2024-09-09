# PR Reminder Bot

A Slack bot built using the Slack Java SDK to manage and automate PR review processes by tracking statuses, monitoring SLA times, sending reminders for overdue PRs, and notifying users when attention is needed.

## Key Features

- \*\*Submit PRs\*\* to manage and track them in the channel.
- \*\*Track SLA\*\* with reminders for approaching or overdue PRs.
- \*\*Update statuses\*\* with +1 or comment notification buttons.
- \*\*Notify PR authors and reviewers\*\* with status updates.
- \*\*View active PRs\*\* and their current statuses.

## Installation

1. \*\*Clone the Repository\*\*:
   \```bash
   git clone https://github.com/yujiashen/PR-Reminder-Bot-Java.git
   cd PR-Reminder-Bot-Java
   \```

2. \*\*Set Up Environment Variables\*\*:
   - Create a `.env` file in the root directory and add your Slack API credentials:
     \```bash
     SLACK_BOT_TOKEN=xoxb-your-slack-bot-token
     SLACK_SIGNING_SECRET=your-slack-signing-secret
     \```

3. \*\*Build the Project\*\*:
   - Make sure you have Maven installed.
   - Navigate to the project directory and build the project:
     \```bash
     mvn clean install
     \```

4. \*\*Run the Bot\*\*:
   After building, you can run the bot using:
   \```bash
   java -jar target/pr-reminder-bot.jar
   \```

5. \*\*Start the Cron Job\*\*:
   The bot uses a cron job to check SLA times and notify channels periodically. To start the cron job:
   - Open the terminal and run:
     \```bash
     crontab -e
     \```
   - Add the following line to schedule the job (modify the path as necessary):
     \```
     0 9-16 \* \* 1-5 /usr/bin/java -jar /path/to/PR-Reminder-Bot-Java/target/pr-reminder-bot.jar
     \```
     This cron job will run every hour from 9 AM to 4 PM PST, Monday through Friday.
   - Save and close the cron file.

## Usage

- \*\*PR Submission\*\*: To submit a PR for review, use the \`/pr-submit\` command and enter the details.
- \*\*Active PRs\*\*: Use \`/pr-active\` to see the current status of all active PRs in the channel.
- \*\*Settings\*\*: Use \`/pr-settings\` to adjust SLA time and notification frequency by channel (default is 8 hours and every hour from 9 to 4 PST).

## Commands

| Command       | Description                                      |
| ------------- | ------------------------------------------------ |
| \`/pr-submit\`  | Submit a PR for review                           |
| \`/pr-active\`  | View all active PRs in the current channel        |
| \`/pr-settings\`| Configure SLA settings                           |

## Development Note

This bot currently uses [Moto](https://github.com/spulec/moto) to mock AWS DynamoDB for testing purposes. 

To run tests:
\```bash
mvn test
\```

## Requirements

- \*\*Java\*\*: Ensure that you have Java 11+ installed.
- \*\*Maven\*\*: Maven is used for managing dependencies and building the project.
