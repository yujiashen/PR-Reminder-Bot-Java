from slack_sdk import WebClient
from logging import Logger
from database import get_user_prs, remove_pr_by_id  # Database functions
from helpers import get_status

# Function to republish the app home after changes
def update_app_home(client, user_id):
    active_prs = get_user_prs(user_id)

    blocks = [
        {
            "type": "section",
            "text": {
                "type": "mrkdwn",
                "text": f"*Welcome back, <@{user_id}>!* :house:\n"
            }
        },
        {
            "type": "divider"
        },
        {
            "type": "section",
            "text": {
                "type": "mrkdwn",
                "text": "*Your Active PRs*"
            }
        }
    ]

    if active_prs:
        for pr in active_prs:
            pr_status = get_status(pr)
            blocks.append({
                "type": "section",
                "text": {
                    "type": "mrkdwn",
                    "text": f"• <{pr['permalink']}|{pr['name']}> - {pr_status}"
                },
                "accessory": {
                    "type": "button",
                    "text": {
                        "type": "plain_text",
                        "text": "Remove"
                    },
                    "style": "danger",
                    "action_id": f"removePr_home_{pr['id']}"
                }
            })
    else:
        blocks.append({
            "type": "section",
            "text": {
                "type": "mrkdwn",
                "text": "You have no active PRs."
            }
        })

    client.views_publish(
        user_id=user_id,
        view={
            "type": "home",
            "blocks": blocks
        }
    )

# Handler for the remove button
def handle_remove_pr_home(ack, body, client, logger):
    ack()
    pr_id = body["actions"][0]["action_id"].split("_")[-1]  # Extract PR ID
    user_id = body["user"]["id"]
    
    try:
        # Remove PR from the store
        remove_pr_by_id(pr_id)
        logger.info(f"PR {pr_id} removed successfully.")

        # Update the app home after removing the PR
        update_app_home(client, user_id)

    except Exception as e:
        logger.error(f"Error removing PR {pr_id}: {e}")

# App home opened event callback
def app_home_opened_callback(client: WebClient, event: dict, logger: Logger):
    if event["tab"] != "home":
        return
    
    try:
        user_id = event["user"]
        
        # Fetch the user's active PRs
        active_prs = get_user_prs(user_id)

        blocks = [
            {
                "type": "section",
                "text": {
                    "type": "mrkdwn",
                    "text": f"*Welcome back, <@{user_id}>!* :house:"
                }
            },
            {
                "type": "divider"
            },
            {
                "type": "section",
                "text": {
                    "type": "mrkdwn",
                    "text": "*Your Active PRs*"
                }
            }
        ]

        if active_prs:
            for pr in active_prs:
                pr_status = get_status(pr)
                blocks.append({
                    "type": "section",
                    "text": {
                        "type": "mrkdwn",
                        "text": f"• <{pr['permalink']}|{pr['name']}> - {pr_status}"
                    },
                    "accessory": {
                        "type": "button",
                        "text": {
                            "type": "plain_text",
                            "text": "Remove"
                        },
                        "style": "danger",
                        "action_id": f"removePr_home_{pr['id']}"
                    }
                })
        else:
            blocks.append({
                "type": "section",
                "text": {
                    "type": "mrkdwn",
                    "text": "You have no active PRs."
                }
            })

        # Publish the home view
        client.views_publish(
            user_id=user_id,
            view={
                "type": "home",
                "blocks": blocks
            }
        )

    except Exception as e:
        logger.error(f"Error publishing home tab: {e}")