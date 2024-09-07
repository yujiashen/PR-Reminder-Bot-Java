import time
import threading
from slack_bolt import Ack
from slack_sdk import WebClient
from logging import Logger
from database import get_pr_by_id, add_pr_to_store

def handle_remove_reviewer(ack: Ack, body: dict, client: WebClient, logger: Logger):
    try:
        ack()

        action_id = body["actions"][0]["action_id"]
        _, reviewer_id, pr_id = action_id.split("_")
        pr = get_pr_by_id(pr_id)

        if pr:
            pr["pending_removals_review"] = pr.get("pending_removals_review", [])
            if reviewer_id not in pr["pending_removals_review"]:
                pr["pending_removals_review"].append(reviewer_id)
            
            add_pr_to_store(pr)

            update_pr_modal_view(client, pr_id, pr, body["view"]["id"])

    except Exception as e:
        logger.error(e)

def handle_remove_attention(ack: Ack, body: dict, client: WebClient, logger: Logger):
    try:
        ack()

        action_id = body["actions"][0]["action_id"]
        _, user_id, pr_id = action_id.split("_")
        pr = get_pr_by_id(pr_id)

        if pr:
            pr["pending_removals_attention"] = pr.get("pending_removals_attention", [])
            if user_id not in pr["pending_removals_attention"]:
                pr["pending_removals_attention"].append(user_id)
            
            add_pr_to_store(pr)

            update_pr_modal_view(client, pr_id, pr, body["view"]["id"])

    except Exception as e:
        logger.error(e)


def handle_ping_attention(ack: Ack, body: dict, client: WebClient, logger: Logger):
    try:
        ack()

        action_id = body["actions"][0]["action_id"]
        _, user_id, pr_id = action_id.split("_")

        pr = get_pr_by_id(pr_id)

        if pr:
            # Ensure 'pinged_users' exists in the PR data as a dictionary
            if 'pinged_users_attention_pending' not in pr:
                pr['pinged_users_attention_pending'] = []

            # Set user_id in the dictionary of pinged users to True
            pr['pinged_users_attention_pending'].append(user_id)

            # Remove the attention request after pinging the user
            pr["pending_removals_attention"] = pr.get("pending_removals_attention", [])
            if user_id not in pr["pending_removals_attention"]:
                pr["pending_removals_attention"].append(user_id)

            add_pr_to_store(pr)

            update_pr_modal_view(client, pr_id, pr, body["view"]["id"])

    except Exception as e:
        logger.error(e)


def update_pr_modal_view(client: WebClient, pr_id: str, pr: dict, view_id: str):
    reviewers_minus_pending = pr['reviewers'].keys() - pr.get("pending_removals_review", [])
    attention_minus_pending = pr['attention_requests'].keys() - pr.get("pending_removals_attention", [])

    reviewer_blocks = [
        {
            "type": "section",
            "text": {
                "type": "mrkdwn",
                "text": f"*{client.users_info(user=reviewer_id)['user']['real_name']}*"
            },
            "accessory": {
                "type": "button",
                "text": {"type": "plain_text", "text": "Remove"},
                "style": "danger",
                "action_id": f"removeReviewer_{reviewer_id}_{pr_id}"
            }
        }
        for reviewer_id in reviewers_minus_pending
    ]

    # Add the "Review updated, ping previous reviewers" button only if there are reviewers
    if pr["reviewers"]:
        reviewer_blocks.append({
            "type": "actions",
            "elements": [
                {
                    "type": "button",
                    "text": {
                        "type": "plain_text",
                        "text": "Ping previous reviewers to redo +1"
                    },
                    "action_id": f"ping_previous_reviewers_button_{pr_id}",
                    "value": pr_id
                }
            ]
        })

    attention_blocks = []
    for user_id in attention_minus_pending:        
        pinged_button_element = {
            "type": "button",
            "text": {
                "type": "plain_text",
                "text": "Ping & Remove",
                "emoji": True
            },
            "action_id": f"pingAttention_{user_id}_{pr_id}",
            "style": "primary",
            "value": user_id
        }
            
        attention_blocks.append({
            "type": "section",
            "text": {
                "type": "mrkdwn",
                "text": f"<@{user_id}>"
            }
        })
        attention_blocks.append({
            "type": "actions",
            "elements": [
                pinged_button_element,
                {
                    "type": "button",
                    "text": {"type": "plain_text", "text": "Remove"},
                    "style": "danger",
                    "action_id": f"removeAttention_{user_id}_{pr_id}"
                }
            ]
        })

    blocks = [
        {
            "type": "input",
            "block_id": "pr_name_block",
            "label": {
                "type": "plain_text",
                "text": "PR Name",
            },
            "element": {
                "type": "plain_text_input",
                "action_id": "pr_name",
                "initial_value": pr["name"]
            }
        },
        {
            "type": "input",
            "block_id": "pr_link_block",
            "label": {
                "type": "plain_text",
                "text": "PR Link",
            },
            "element": {
                "type": "plain_text_input",
                "action_id": "pr_link",
                "initial_value": pr["link"]
            }
        },
        {
            "type": "input",
            "block_id": "pr_description_block",
            "label": {
                "type": "plain_text",
                "text": "PR Description",
            },
            "optional": True,
            "element": {
                "type": "plain_text_input",
                "action_id": "pr_description",
                "initial_value": pr["description"]
            }
        },
        {
            "type": "input",
            "optional": True,
            "block_id": "reviews_needed_block",
            "label": {
                "type": "plain_text",
                "text": "Reviews Needed",
            },
            "element": {
                "type": "plain_text_input",
                "action_id": "reviews_needed",
                "initial_value": str(pr["reviews_needed"])
            }
        },
        {
            "type": "divider"
        },
        {
            "type": "section",
            "text": {
                "type": "mrkdwn",
                "text": "*Reviewers*"
            }
        },
        *reviewer_blocks,
        {
            "type": "divider"
        },
        {
            "type": "section",
            "text": {
                "type": "mrkdwn",
                "text": "*Attention Requested*"
            }
        },
        *attention_blocks
    ]

    client.views_update(
        view_id=view_id,
        view={
            "type": "modal",
            "callback_id": "edit_submit_modal",
            "private_metadata": pr_id,
            "title": {
                "type": "plain_text",
                "text": "Edit PR",
            },
            "blocks": blocks,
            "submit": {
                "type": "plain_text",
                "text": "Save Changes"
            }
        }
    )
