from slack_bolt import Ack
from slack_sdk import WebClient
from logging import Logger
from .block_helpers import assemble_pr_message_blocks
from database import get_pr_by_id, add_pr_to_store

def handle_plus_one(ack: Ack, body: dict, client: WebClient, logger: Logger):
    try:
        ack()

        user_id = body["user"]["id"]
        action_id = body["actions"][0]["action_id"]
        pr_id = action_id.replace("plus_one_", "")

        pr = get_pr_by_id(pr_id)

        if pr:
            # Toggle the +1 status
            if user_id in pr["reviewers"]:
                # User has already +1'd, remove the +1
                del pr["reviewers"][user_id]
                pr["reviews_received"] -= 1
            else:
                # User is +1'ing the PR, add it
                pr["reviewers"][user_id] = True
                pr["reviews_received"] += 1
            
            add_pr_to_store(pr)
            
            # Fetch the updated list of reviewers
            reviewer_names = []
            for reviewer in pr["reviewers"]:
                user_info = client.users_info(user=reviewer)
                if user_info.get("ok"):
                    real_name = user_info["user"]["real_name"]
                    reviewer_names.append(real_name)
                else:
                    logger.error(f"Failed to fetch user info for user ID {reviewer}")

            reviewers_text = ", ".join(reviewer_names) if reviewer_names else "None"
            reviews_needed = pr["reviews_needed"] - pr["reviews_received"]

            # Construct the main message text
            message_text = f"*<{pr['link']}|{pr['name']}>*\n{pr['description']}"
            if reviews_needed <= 0:
                message_text += (
                    f"\n\n:white_check_mark: Your PR was reviewed by {reviewers_text}!\n"
                    f"It’s ready to be merged.\n"
                    f"Please merge the PR and remove it from the queue, or update the request if needed."
                )
                client.chat_postMessage(
                    channel=pr['submitter_id'],
                    text= f":white_check_mark: Your PR *<{pr['link']}|{pr['name']}>* has been reviewed. Please remove it from queue or update the request.",
                    blocks= [
                        {
                            "type": "section",
                            "text": {
                                "type": "mrkdwn",
                                "text": f":white_check_mark: *Your PR* *<{pr['link']}|{pr['name']}>* *has been reviewed*.\nPlease remove it from the queue or update the request.\n<https://your-workspace.slack.com/archives/{pr['channel_id']}/p{pr['message_ts'].replace('.', '')}|View original post>"
                            }
                        }
                    ],
                    unfurl_links = False
                )
            else:
                message_text += f"\nNeeds {reviews_needed} more reviews\n\n*Reviewers:* {reviewers_text}"

            # Reconstruct the blocks with updated information
            blocks = assemble_pr_message_blocks(client, pr, user_id)

            # Update the original message with the new blocks
            client.chat_update(
                channel=pr["channel_id"],
                ts=pr["message_ts"],
                blocks=blocks,
                text=message_text
            )

    except Exception as e:
        logger.error(f"Error in handle_plus_one: {e}")
