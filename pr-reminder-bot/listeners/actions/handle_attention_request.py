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

        pr = get_pr_by_id(pr_id)

        if pr:
            if user_id in pr["attention_requests"]:
                del pr["attention_requests"][user_id]
            else:
                pr["attention_requests"][user_id] = True

                submitter_id = pr.get("submitter_id")
                if not submitter_id:
                    logger.error(f"Submitter ID for PR {pr_id} not found.")
                    return
                
                user_id_name = client.users_info(user=user_id)["user"]["real_name"]
                original_post_ts = pr.get('message_ts')

                # Send a DM to the submitter
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

            # Update the attention_requests in the database
            add_pr_to_store(pr)

            # Update the original message with the new blocks
            blocks = assemble_pr_message_blocks(client, pr, user_id)
            client.chat_update(
                channel=pr["channel_id"],
                ts=pr["message_ts"],
                blocks=blocks,
                text=blocks[0]["text"]["text"]
            )

    except Exception as e:
        logger.error(e)