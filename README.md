# PR Reminder Bot

A Slack bot to manage and automate PR review processes by tracking statuses, monitoring SLA times, sending reminders for overdue PRs, and notifying users when attention is needed

## Key Features

- **Submit PRs** to manage and track them in the channel.
- **Track SLA** with reminders for approaching or overdue PRs.
- **Update statuses** with +1 or comment notification buttons.
- **Notify PR authors and reviewers** with status updates.
- **View active PRs** and their current statuses.

## Installation

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/yujiashen/PR-Reminder-Bot.git
   cd PR-Reminder-Bot
   ```

2. **Setup Environment Variables**:
   - Create a `.env` file in the root directory and add your Slack API credentials:
     ```bash
     SLACK_BOT_TOKEN=xoxb-your-slack-bot-token
     SLACK_SIGNING_SECRET=your-slack-signing-secret
     ```

3. **Run the Bot**:
   Nevigate to the `src` directory and start the bot by running the following command:
   ```bash
   cd src
   python app.py
   ```

4. **Start the Cron Job**:
The bot uses a cron job to check SLA times and notify channels periodically. To start the cron job:
- Open the terminal and run:
    ```bash
    crontab -e
    ```
- Add the following line to schedule the job (modify the path as necessary):
    ```
    0 9-16 * * 1-5 /usr/bin/python /path/to/PR-Reminder-Bot/src/app.py
    ```
    This cron job will run every hour from 9 AM to 4 PM PST, Monday through Friday.
- Save and close the cron file.

## Usage

- **PR Submission**: To submit a PR for review, use the `/pr-submit` and enter the details
- **Active PRs**: Use `/pr-active` to see the current status of all active PRs in the channel.
- **Settings**: Use `/pr-settings` to adjust SLA time and notification frequency by channel (default is 8 hours and every hour from 9 to 4 PST)

## Commands

| Command       | Description                                      |
| ------------- | ------------------------------------------------ |
| `/pr-submit`  | Submit a PR for review                           |
| `/pr-active`  | View all active PRs in the current channel        |
| `/pr-settings`| Configure SLA settings            |

## Development Note

This bot currently uses [Moto](https://github.com/spulec/moto) to mock AWS DynamoDB for testing purposes.