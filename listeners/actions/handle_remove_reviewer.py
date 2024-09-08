from slack_bolt import Ack
from slack_sdk import WebClient
from logging import Logger
from database import get_pr_by_id, add_pr_to_store

def handle_remove_reviewer(ack: Ack, body: dict, client: WebClient, logger: Logger):
    try:
        ack()
        action_id = body["actions"][0]["action_id"]
        _, reviewer_id, pr_id = action_id.split("_")
        
        logger.info(f"Attempting to remove reviewer {reviewer_id} from PR {pr_id}")
        pr = get_pr_by_id(pr_id)

        if pr:
            pr["pending_removals_review"] = pr.get("pending_removals_review", [])
            if reviewer_id not in pr["pending_removals_review"]:
                pr["pending_removals_review"].append(reviewer_id)
                logger.info(f"Reviewer {reviewer_id} marked for removal from PR {pr_id}")
            
            add_pr_to_store(pr)
            logger.info(f"PR {pr_id} updated in the store after marking reviewer {reviewer_id} for removal.")

            update_pr_modal_view(client, pr_id, pr, body["view"]["id"], logger)
        else:
            logger.warning(f"PR {pr_id} not found when attempting to remove reviewer {reviewer_id}")

    except Exception as e:
        logger.error(f"Error removing reviewer {reviewer_id} from PR {pr_id}: {e}")

def handle_remove_attention(ack: Ack, body: dict, client: WebClient, logger: Logger):
    try:
        ack()
        action_id = body["actions"][0]["action_id"]
        _, user_id, pr_id = action_id.split("_")
        
        logger.info(f"Attempting to remove attention request for user {user_id} from PR {pr_id}")
        pr = get_pr_by_id(pr_id)

        if pr:
            pr["pending_removals_attention"] = pr.get("pending_removals_attention", [])
            if user_id not in pr["pending_removals_attention"]:
                pr["pending_removals_attention"].append(user_id)
                logger.info(f"User {user_id} marked for attention removal from PR {pr_id}")
            
            add_pr_to_store(pr)
            logger.info(f"PR {pr_id} updated in the store after marking user {user_id} for attention removal.")

            update_pr_modal_view(client, pr_id, pr, body["view"]["id"], logger)
        else:
            logger.warning(f"PR {pr_id} not found when attempting to remove attention for user {user_id}")

    except Exception as e:
        logger.error(f"Error removing attention for user {user_id} from PR {pr_id}: {e}")

def handle_ping_attention(ack: Ack, body: dict, client: WebClient, logger: Logger):
    try:
        ack()
        action_id = body["actions"][0]["action_id"]
        _, user_id, pr_id = action_id.split("_")
        
        logger.info(f"Pinging attention request for user {user_id} in PR {pr_id}")
        pr = get_pr_by_id(pr_id)

        if pr:
            if 'pinged_users_attention_pending' not in pr:
                pr['pinged_users_attention_pending'] = []
            
            pr['pinged_users_attention_pending'].append(user_id)
            logger.info(f"User {user_id} added to pinged attention pending list for PR {pr_id}")

            pr["pending_removals_attention"] = pr.get("pending_removals_attention", [])
            if user_id not in pr["pending_removals_attention"]:
                pr["pending_removals_attention"].append(user_id)
                logger.info(f"User {user_id} marked for attention removal after ping in PR {pr_id}")

            add_pr_to_store(pr)
            logger.info(f"PR {pr_id} updated in the store after pinging user {user_id} for attention.")

            update_pr_modal_view(client, pr_id, pr, body["view"]["id"], logger)
        else:
            logger.warning(f"PR {pr_id} not found when attempting to ping attention for user {user_id}")

    except Exception as e:
        logger.error(f"Error pinging attention for user {user_id} in PR {pr_id}: {e}")

def update_pr_modal_view(client: WebClient, pr_id: str, pr: dict, view_id: str, logger: Logger):
    try:
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
        logger.info(f"PR modal view updated successfully for PR {pr_id}.")
    
    except Exception as e:
        logger.error(f"Error updating PR modal view for PR {pr_id}: {e}")
