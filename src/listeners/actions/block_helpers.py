from slack_sdk import WebClient
from logging import Logger
from datetime import datetime

def assemble_pr_message_blocks(client: WebClient, pr: dict, user_id: str, logger: Logger) -> list:
    try:
        blocks = create_pr_message_block(client, pr, logger)
        logger.info(f"Assembling PR message blocks for PR {pr['id']} by user {user_id}")

        # Append the necessary buttons
        blocks[1]["elements"].append(create_plus_one_button(pr["id"], user_id, pr))
        blocks[1]["elements"].append(create_question_mark_button(pr["id"], user_id, pr))
        blocks[1]["elements"].append(create_remove_button(pr["id"]))
        blocks[1]["elements"].append(create_edit_button(pr["id"]))

        return blocks
    except Exception as e:
        logger.error(f"Error assembling message blocks for PR {pr['id']}: {e}")
        raise

def create_pr_message_block(client: WebClient, pr: dict, logger: Logger) -> list:
    try:
        # Get the submitter's Slack user mention and real name
        submitter_info = client.users_info(user=pr['submitter_id'])
        submitter_name = submitter_info["user"]["real_name"]
        submission_time = datetime.fromisoformat(pr['timestamp']).strftime('%b %-d, %-I:%M %p')

        # Create the list of reviewers who have +1'ed the PR
        reviewers_text = ", ".join(
            [f"*{client.users_info(user=r)['user']['real_name']}*" for r in pr["reviewers"]]
        )
        reviews_needed = pr["reviews_needed"] - pr["reviews_received"]

        # Construct the message with enhanced styling
        message_text = (
            f":memo: *PR Reviews Requested!*\n\n"  # Heading with an icon
            f"*<{pr['link']}|{pr['name']}>*\n"
            f"{pr['description']}\n\n"
            f":bust_in_silhouette: *Submitted by:* *{submitter_name}* at {submission_time}\n"
        )
        
        if reviews_needed <= 0:
            submitter_mention = f"<@{pr['submitter_id']}>"
            message_text += (
                f"\n\n:white_check_mark: Your PR was reviewed by {reviewers_text}!\n"
                f"It’s ready to be merged.\n"
                f"Please merge the PR and remove it from the queue, or update the request if needed."
            )
        else:
            message_text += (
                f"\n:hourglass_flowing_sand: Needs {reviews_needed} more reviews\n\n"
                f":white_check_mark: *+1s:* {reviewers_text}"
            )

        # Add Attention requested line
        if pr["attention_requests"]:
            attention_requested_by = ", ".join(
                [f"*{client.users_info(user=user_id)['user']['real_name']}*" for user_id in pr["attention_requests"]]
            )
            message_text += f"\n\n:speech_balloon: *Attention requested by:* {attention_requested_by}"

        return [
            {
                "type": "section",
                "text": {
                    "type": "mrkdwn",
                    "text": message_text,
                },
            },
            {
                "type": "actions",
                "elements": [],  # Start with an empty list for elements
            }
        ]
    except Exception as e:
        logger.error(f"Error creating PR message block for PR {pr['id']}: {e}")
        raise

def create_plus_one_button(pr_id: str, user_id: str, pr: dict) -> dict:
    return {
        "type": "button",
        "text": {
            "type": "plain_text",
            "text": "+1",
            "emoji": True
        },
        "action_id": f"plus_one_{pr_id}",
        "value": pr_id,
    }

def create_remove_button(pr_id: str) -> dict:
    return {
        "type": "button",
        "text": {
            "type": "plain_text",
            "text": "Remove",
        },
        "action_id": f"remove_pr_{pr_id}",
        "value": pr_id,
        "confirm": {
        "title": {
            "type": "plain_text",
            "text": "Are you sure?"
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

def create_edit_button(pr_id: str) -> dict:
    return {
        "type": "button",
        "text": {
            "type": "plain_text",
            "text": "Edit",
        },
        "action_id": f"edit_pr_{pr_id}",
        "value": pr_id,
    }

def create_question_mark_button(pr_id: str, user_id: str, pr: dict) -> dict:
    question_mark_button = {
        "type": "button",
        "text": {
            "type": "plain_text",
            "text": ":speech_balloon:",
            "emoji": True
        },
        "action_id": f"attention_request_{pr_id}",
        "value": pr_id,
    }
    if pr["attention_requests"]:
        question_mark_button["style"] = "danger"

    return question_mark_button
