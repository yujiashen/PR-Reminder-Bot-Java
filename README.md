# PR Reminder Bot

A Slack bot designed to streamline the PR review process by tracking review statuses, sending reminders, and notifying users when their attention is needed.

## Features

- **Submit PRs for Review**: Easily submit PRs for review via Slack using the `/pr-submit` command.
- **Track Review Statuses**: Automatically update and track the status of PRs (needs review, in progress, completed).
- **SLA Reminders**: Set configurable SLAs (default: 8 hours) to ensure timely reviews. If a PR exceeds the SLA, the bot sends reminders to the reviewers.
- **Adjust Review Requirements**: Dynamically configure the number of reviews required for a PR to be approved.
- **Notify Reviewers**: Ping reviewers when their attention is needed or after a PR update.
- **App Home Integration**: View a list of active PRs in the Slack app home, showing their statuses and a remove option.
- **Channel Slash Commands**:
  - `/pr-submit`: Submit a PR for review.
  - `/pr-active`: View all active PRs in the current channel.
  - `/pr-settings`: Configure SLA and review settings.

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
   Start the bot by running the following command:
   ```bash
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
    0 9-16 * * 1-5 /usr/bin/python /path/to/PR-Reminder-Bot/app.py
    ```
    This cron job will run every hour from 9 AM to 4 PM PST, Monday through Friday.
- Save and close the cron file.

## Usage

- **PR Submission**: To submit a PR for review, use the `/pr-submit` slash command followed by the PR link.
- **Active PRs**: Use `/pr-active` to see the current status of all active PRs in the channel.
- **Settings**: Use `/pr-settings` to adjust SLA settings or change the number of reviews required for approval.

## Commands

| Command       | Description                                      |
| ------------- | ------------------------------------------------ |
| `/pr-submit`  | Submit a PR for review                           |
| `/pr-active`  | View all active PRs in the current channel        |
| `/pr-settings`| Configure SLA and review requirements            |

## Development Note

This bot currently uses [Moto](https://github.com/spulec/moto) to mock AWS DynamoDB for testing purposes.