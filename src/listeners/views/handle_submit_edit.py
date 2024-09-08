from ..actions.block_helpers import assemble_pr_message_blocks
from database import get_pr_by_id, add_pr_to_store
from helpers import is_valid_int

def handle_submit_edit(ack, body, client, logger):
    try:
        pr_name = body["view"]["state"]["values"]["pr_name_block"]["pr_name"]["value"]
        pr_link = body['view']['private_metadata']
        pr_description = body["view"]["state"]["values"]["pr_description_block"]["pr_description"]["value"] or ""
        reviews_needed = body["view"]["state"]["values"]["reviews_needed_block"]["reviews_needed"]["value"]
        pr_id = pr_link

        logger.info(f"Handling submit edit for PR {pr_id} with name {pr_name}")

        if not reviews_needed:
            reviews_needed = 2
        if not is_valid_int(reviews_needed):
            ack(response_action="errors", errors={
                "reviews_needed_block": "Please enter a valid number."
            })
            logger.warning(f"Invalid reviews_needed input: {reviews_needed}")
            return
        reviews_needed = int(reviews_needed) if reviews_needed else 2

        pr = get_pr_by_id(pr_id)
        
        if pr:  # Existing PR
            ack()
            user_id = body["user"]["id"]
            logger.info(f"Found existing PR {pr_id}, updating it.")

            # Finalize removals
            if pr and "pending_removals_review" in pr:
                for reviewer_id in pr["pending_removals_review"]:
                    if reviewer_id in pr["reviewers"]:
                        del pr["reviewers"][reviewer_id]
                        pr["reviews_received"] -= 1
                del pr["pending_removals_review"]

            if pr and "pending_removals_attention" in pr:
                for attention_id in pr["pending_removals_attention"]:
                    if attention_id in pr["attention_requests"]:
                        del pr["attention_requests"][attention_id]
                del pr["pending_removals_attention"]

            # Handle pinged users for attention
            pinged_usernames_attention = []
            if pr and 'pinged_users_attention_pending' in pr:
                for user_to_ping in pr['pinged_users_attention_pending']:
                    user_info = client.users_info(user=user_to_ping)
                    pinged_usernames_attention.append(user_info['user']['real_name'])
                    try:
                        client.chat_postMessage(
                            channel=user_to_ping,
                            text=f"Your review for PR {pr['name']} has been addressed. The PR has been updated with new changes or responses to your comments. Please review the latest updates. [View original post](https://your-workspace.slack.com/archives/{pr['channel_id']}/p{pr['message_ts'].replace('.', '')})",
                            blocks=[
                                {
                                    "type": "section",
                                    "text": {
                                        "type": "mrkdwn",
                                        "text": f"*Your review for PR* *<{pr['link']}|{pr['name']}>* *has been addressed.*\nThe PR has been updated with new changes or responses to your comments.Please review the latest updates.\n<https://your-workspace.slack.com/archives/{pr['channel_id']}/p{pr['message_ts'].replace('.', '')}|View original post>",
                                    }
                                }
                            ],
                            unfurl_links=False
                        )
                        logger.info(f"Sent DM to {user_to_ping} regarding addressed review for PR {pr['name']}.")
                    except Exception as e:
                        logger.error(f"Failed to send message to {user_to_ping}: {e}")
                
                pinged_names_attention_str = ", ".join(pinged_usernames_attention)
                client.chat_postEphemeral(
                    channel=pr["channel_id"],
                    user=body["user"]["id"],
                    text=f"The following users have been pinged: {pinged_names_attention_str}."
                )
                del pr['pinged_users_attention_pending']

            # Handle pinged users for redo
            if pr and 'pinged_users_redo_pending' in pr:
                for user_to_ping in pr['pinged_users_redo_pending']:
                    user_info = client.users_info(user=user_to_ping)
                    try:
                        client.chat_postMessage(
                            channel=user_to_ping,
                            text=f"*The PR* *<{pr['link']}|{pr['name']}>* *has been updated since your last review.*\nPlease take a moment to review the changes and update your +1 if you still approve.\n<https://your-workspace.slack.com/archives/{pr['channel_id']}/p{pr['message_ts'].replace('.', '')}|View original post>",
                            blocks=[
                                {
                                    "type": "section",
                                    "text": {
                                        "type": "mrkdwn",
                                        "text": f"*The PR* *<{pr['link']}|{pr['name']}>* *has been updated since your last review.*\nPlease take a moment to review the changes and update your +1 if you still approve.\n<https://your-workspace.slack.com/archives/{pr['channel_id']}/p{pr['message_ts'].replace('.', '')}|View original post>",
                                    }
                                }
                            ],
                            unfurl_links=False
                        )
                        logger.info(f"Sent DM to {user_to_ping} regarding updated review for PR {pr['name']}.")
                    except Exception as e:
                        logger.error(f"Failed to send message to {user_to_ping}: {e}")
                
                client.chat_postEphemeral(
                    channel=pr["channel_id"],
                    user=body["user"]["id"],
                    text="Previous reviewers have been pinged."
                )
                del pr['pinged_users_redo_pending']

            pr["name"] = pr_name
            pr["link"] = pr_link
            pr["description"] = pr_description
            pr["reviews_needed"] = reviews_needed

            blocks = assemble_pr_message_blocks(client, pr, user_id, logger)
            client.chat_update(
                channel=pr["channel_id"],
                ts=pr["message_ts"],
                blocks=blocks,
                text=blocks[0]["text"]["text"]
            )
            add_pr_to_store(pr)
            logger.info(f"PR {pr_id} updated successfully.")
        else:
            ack(response_action="errors", errors={
                "pr_description_block": "PR not found."
            })
            logger.error(f"PR with ID {pr_id} not found for editing.")

    except Exception as e:
        logger.error(f"Error during PR edit submission: {e}")
