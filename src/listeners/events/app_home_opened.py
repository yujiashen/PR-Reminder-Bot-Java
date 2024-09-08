from slack_sdk import WebClient
from logging import Logger
from database import get_user_prs
from helpers import get_status
from ..actions.handle_remove_pr import handle_remove_pr

# App home opened event callback
def app_home_opened_callback(client: WebClient, event: dict, logger: Logger):
    if event["tab"] != "home":
        return

    try:
        user_id = event["user"]
        logger.info(f"App home opened by user {user_id}.")
        
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
            logger.info(f"User {user_id} has {len(active_prs)} active PRs.")
            for pr in active_prs:
                pr_status = get_status(pr)
                reviewers_text = ""
                if pr['reviewers']:
                    reviewers_text = " - reviewed by "
                    reviewers_text += ", ".join(
                        [f"<@{r}>" for r in pr["reviewers"]]
                    )
                blocks.append({
                    "type": "section",
                    "text": {
                        "type": "mrkdwn",
                        "text": f"• *<{pr['permalink']}|{pr['name']}>* - {pr_status}{reviewers_text}"
                    }
                })
                blocks.append({
                    "type": "actions",
                    "elements": [
                        {
                            "type": "button",
                            "text": {
                                "type": "plain_text",
                                "text": "Remove"
                            },
                            "style": "danger",
                            "action_id": f"removePr_home_{pr['id']}",
                            "confirm": {
                                "title": {
                                    "type": "plain_text",
                                    "text": "Remove this PR?"
                                },
                                "text": {
                                    "type": "mrkdwn",
                                    "text": "This action cannot be undone."
                                },
                                "confirm": {
                                    "type": "plain_text",
                                    "text": "Yes, remove it"
                                },
                                "deny": {
                                    "type": "plain_text",
                                    "text": "Cancel"
                                }
                            }
                        }
                    ]
                })
        else:
            logger.info(f"No active PRs found for user {user_id}.")
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
        logger.info(f"App home view published successfully for user {user_id}.")

    except Exception as e:
        logger.error(f"Error publishing home tab for user {user_id}: {e}")


# Handler for the remove button
def handle_remove_pr_home(ack, body, client, logger):
    ack()
    pr_id = body["actions"][0]["action_id"].split("_")[-1]  # Extract PR ID
    user_id = body["user"]["id"]
    
    try:
        handle_remove_pr(ack, body, client, logger)
        # Update the app home after removing the PR
        update_app_home(client, user_id, logger)

    except Exception as e:
        logger.error(f"Error removing PR {pr_id}: {e}")


# Function to republish the app home after changes
def update_app_home(client, user_id, logger):
    logger.info(f"Updating app home for user {user_id}")
    
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
        logger.info(f"User {user_id} has {len(active_prs)} active PRs.")
        for pr in active_prs:
            pr_status = get_status(pr)
            reviewers_text = ""
            if pr['reviewers']:
                reviewers_text = " - reviewed by "
                reviewers_text += ", ".join(
                    [f"<@{r}>" for r in pr["reviewers"]]
                )
            blocks.append({
                "type": "section",
                "text": {
                    "type": "mrkdwn",
                    "text": f"• *<{pr['permalink']}|{pr['name']}>* - {pr_status}{reviewers_text}"
                }
            })
            blocks.append({
                "type": "actions",
                "elements": [
                    {
                        "type": "button",
                        "text": {
                            "type": "plain_text",
                            "text": "Remove"
                        },
                        "style": "danger",
                        "action_id": f"removePr_home_{pr['id']}",
                        "confirm": {
                            "title": {
                                "type": "plain_text",
                                "text": "Remove this PR?"
                            },
                            "text": {
                                "type": "mrkdwn",
                                "text": "This action cannot be undone."
                            },
                            "confirm": {
                                "type": "plain_text",
                                "text": "Yes, remove it"
                            },
                            "deny": {
                                "type": "plain_text",
                                "text": "Cancel"
                            }
                        }
                    }
                ]
            })
    else:
        logger.info(f"No active PRs found for user {user_id}.")
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
    logger.info(f"App home view updated successfully for user {user_id}.")