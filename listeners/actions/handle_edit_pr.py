from slack_bolt import Ack
from slack_sdk import WebClient
from logging import Logger
from database import get_pr_by_id

def handle_edit_pr(ack: Ack, body: dict, client: WebClient, logger: Logger):
    try:
        # TO DO
        if not is_valid_int(reviews_needed):
            ack(response_action="errors", errors={
                "reviews_needed_block": "Please enter a valid number."
            })
            return
        
        ack()

        user_id = body["user"]["id"]  # ID of the user trying to edit the PR
        action_id = body["actions"][0]["action_id"]
        pr_id = action_id.replace("edit_pr_", "")

        pr = get_pr_by_id(pr_id)

        if pr:
            if user_id != pr["submitter_id"]:
                # If the user is not the submitter, deny access
                client.chat_postEphemeral(
                    channel=pr["channel_id"],
                    user=user_id,
                    text="Only the PR submitter can edit this PR."
                )
                return  # Don't proceed with opening the modal
            
            pr_description = pr["description"] if pr["description"] else ""

            # Create the list of reviewers with remove buttons
            reviewer_blocks = []
            for reviewer_id in pr["reviewers"]:
                user_info = client.users_info(user=reviewer_id)
                reviewer_name = user_info["user"]["real_name"]
                reviewer_blocks.append({
                    "type": "section",
                    "text": {
                        "type": "mrkdwn",
                        "text": f"*{reviewer_name}*"
                    },
                    "accessory": {
                        "type": "button",
                        "text": {"type": "plain_text", "text": "Remove"},
                        "style": "danger",
                        "action_id": f"removeReviewer_{reviewer_id}_{pr_id}"
                    }
                })


            # Add the "Review updated, ping previous reviewers" button only if there are reviewers
            if pr["reviewers"]:
                reviewer_blocks.append({
                    "type": "actions",
                    "elements": [
                        {
                            "type": "button",
                            "text": {
                                "type": "plain_text",
                                "text": "Review updated, ping previous reviewers to redo +1"
                            },
                            "action_id": f"ping_previous_reviewers_button_{pr_id}",
                            "style": "primary",
                            "value": pr_id
                        }
                    ]
                })


            attention_blocks = []
            for user_id in pr["attention_requests"]:
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

            # Open the modal with the current PR data for editing
            client.views_open(
                trigger_id=body["trigger_id"],
                view={
                    "type": "modal",
                    "callback_id": "edit_submit_modal",
                    "private_metadata": pr_id,
                    "title": {
                        "type": "plain_text",
                        "text": "Edit PR",
                    },
                    "blocks": [
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
                            "type": "section",
                            "block_id": "pr_link_block",
                            "text": {
                                "type": "mrkdwn",
                                "text": f"*PR Link:* {pr['link']}"
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
                                "initial_value": pr_description
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
                        # Adding the list of reviewers with remove buttons
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
                        *reviewer_blocks,  # Insert the reviewer blocks here

                        # Adding a section for Attention Requests
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
                        *attention_blocks  # Insert the attention blocks here
                    ],
                    "submit": {
                        "type": "plain_text",
                        "text": "Save Changes"
                    }
                }
            )

    except Exception as e:
        logger.error(e)
