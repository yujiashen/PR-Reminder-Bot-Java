from slack_bolt import Ack, Respond
from slack_sdk import WebClient
from logging import Logger

# Define the callback function at the top level

# Define the callback function at the top level
def submit_pr_callback(command, ack: Ack, respond: Respond, client: WebClient, logger: Logger):
    try:
        ack()
        client.views_open(
            trigger_id=command["trigger_id"],
            view={
                "type": "modal",
                "callback_id": "handle_submit_pr",
                "private_metadata": command["channel_id"],  # Store the channel ID here
                "title": {
                    "type": "plain_text",
                    "text": "Submit PR for Review",
                },
                "blocks": [
                    {
                        "type": "input",
                        "block_id": "pr_name_block",
                        "element": {
                            "type": "plain_text_input",
                            "action_id": "pr_name",
                            "placeholder": {
                                "type": "plain_text",
                                "text": "Enter the PR name"
                            }
                        },
                        "label": {
                            "type": "plain_text",
                            "text": "PR Name"
                        }
                    },
                    {
                        "type": "input",
                        "block_id": "pr_link_block",
                        "element": {
                            "type": "plain_text_input",
                            "action_id": "pr_link",
                            "placeholder": {
                                "type": "plain_text",
                                "text": "Enter the PR link"
                            }
                        },
                        "label": {
                            "type": "plain_text",
                            "text": "PR Link"
                        }
                    },
                    {
                        "type": "input",
                        "optional": True,  # Mark this field as optional
                        "block_id": "pr_description_block",
                        "element": {
                            "type": "plain_text_input",
                            "action_id": "pr_description",
                            "placeholder": {
                                "type": "plain_text",
                                "text": "Enter an optional description"
                            },
                            "multiline": True  # Allow multiline input
                        },
                        "label": {
                            "type": "plain_text",
                            "text": "Description (Optional)"
                        }
                    },
                    {
                        "type": "input",
                        "optional": True,
                        "block_id": "reviews_needed_block",
                        "element": {
                            "type": "plain_text_input",
                            "action_id": "reviews_needed",
                            "placeholder": {
                                "type": "plain_text",
                                "text": "Enter number of reviews needed (default is 2)"
                            }
                        },
                        "label": {
                            "type": "plain_text",
                            "text": "Reviews Needed"
                        }
                    },
                ],
                "submit": {
                    "type": "plain_text",
                    "text": "Submit"
                }
            }
        )
    except Exception as e:
        logger.error(e)
        respond("There was an error opening the modal.")
