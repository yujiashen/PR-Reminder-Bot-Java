from slack_bolt import Ack
from slack_sdk import WebClient
from logging import Logger
from database import get_pr_by_id, remove_pr_by_id

def handle_remove_pr(ack: Ack, body: dict, client: WebClient, logger: Logger):
    try:
        ack()

        # Extract PR ID from the action ID
        action_id = body["actions"][0]["action_id"]
        pr_id = action_id.replace("remove_pr_", "")
        pr = get_pr_by_id(pr_id)

        # Check if the PR exists in the store
        if pr:
            # Inform the channel that the PR is being removed
            client.chat_update(
                channel=pr["channel_id"],
                ts=pr["message_ts"],
                text=f"*<{pr['link']}|{pr['name']}>* has been removed from the review queue.",
                blocks= [
                    {
                        "type": "section",
                        "text": {
                            "type": "mrkdwn",
                            "text": f"*<{pr['link']}|{pr['name']}>* has been removed from the review queue."
                        }
                    }
                ]
            )
            # Remove the PR from the store
            remove_pr_by_id(pr_id)
        else:
            logger.warning(f"PR with ID {pr_id} not found in the store.")

    except Exception as e:
        logger.error(e)