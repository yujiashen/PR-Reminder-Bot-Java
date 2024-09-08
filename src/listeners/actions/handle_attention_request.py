from slack_bolt import Ack
from slack_sdk import WebClient
from logging import Logger
from .block_helpers import assemble_pr_message_blocks
from database import get_pr_by_id, add_pr_to_store

def handle_attention_request(ack: Ack, body: dict, client: WebClient, logger: Logger):
    try:
        ack()

        user_id = body["user"]["id"]
        action_id = body["actions"][0]["action_id"]
        pr_id = action_id.replace("attention_request_", "")
        logger.info(f"User {user_id} is attempting to request attention for PR {pr_id}")

        pr = get_pr_by_id(pr_id)

        if pr:
            if user_id in pr["attention_requests"]:
                del pr["attention_requests"][user_id]
                logger.info(f"User {user_id} removed their attention request from PR {pr_id}")
            else:
                pr["attention_requests"][user_id] = True
                logger.info(f"User {user_id} requested attention for PR {pr_id}")

                submitter_id = pr.get("submitter_id")
                if not submitter_id:
                    logger.error(f"Submitter ID for PR {pr_id} not found.")
                    return
                
                user_id_name = client.users_info(user=user_id)["user"]["real_name"]
                original_post_ts = pr.get('message_ts')

                # Send a DM to the submitter
                try:
                    client.chat_postMessage(
                        channel=submitter_id,
                        text=f"Attention requested by {user_id_name} for your PR {pr['name']}.",
                        blocks= [
                            {
                                "type": "section",
                                "text": {
                                    "type": "mrkdwn",
                                    "text": f"*Attention requested by {user_id_name} for your PR* *<{pr['link']}|{pr['name']}>*.\n<https://your-workspace.slack.com/archives/{pr['channel_id']}/p{original_post_ts.replace('.', '')}|View original post>"
                                }
                            }
                        ],
                        unfurl_links = False
                    )
                    logger.info(f"Sent attention request notification to submitter {submitter_id} for PR {pr_id}")
                except Exception as dm_error:
                    logger.error(f"Failed to send DM to submitter {submitter_id} for PR {pr_id}: {dm_error}")

            # Update the attention_requests in the database
            add_pr_to_store(pr)
            logger.info(f"PR {pr_id} attention requests updated in the store")

            # Update the original message with the new blocks
            blocks = assemble_pr_message_blocks(client, pr, user_id, logger)
            client.chat_update(
                channel=pr["channel_id"],
                ts=pr["message_ts"],
                blocks=blocks,
                text=blocks[0]["text"]["text"]
            )
            logger.info(f"Updated PR message in channel {pr['channel_id']} for PR {pr_id}")

        else:
            logger.error(f"PR {pr_id} not found in the store.")

    except Exception as e:
        logger.error(f"Error handling attention request for PR {pr_id}: {e}")
