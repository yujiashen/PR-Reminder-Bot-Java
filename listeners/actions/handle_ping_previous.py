from slack_bolt import Ack
from slack_sdk import WebClient
from logging import Logger
from .handle_remove_reviewer import update_pr_modal_view
from database import get_pr_by_id, add_pr_to_store

def handle_ping_previous_reviewers(ack: Ack, body: dict, client: WebClient, logger: Logger):
    try:
        ack()  # Acknowledge the action

        # Extract the PR ID from the action
        action_id = body["actions"][0]["action_id"]
        pr_id = action_id.replace("ping_previous_reviewers_button_", "")
        logger.info(f"Handling ping for previous reviewers for PR {pr_id}")

        pr = get_pr_by_id(pr_id)

        # Fetch the PR details from the store
        if pr:
            previous_reviewers = pr.get("reviewers", [])
            pr["pending_removals_review"] = pr.get("pending_removals_review", [])

            if previous_reviewers:
                logger.info(f"Found {len(previous_reviewers)} previous reviewers for PR {pr_id}")
            else:
                logger.info(f"No previous reviewers found for PR {pr_id}")

            # Send a DM to each previous reviewer asking them to redo their +1
            for reviewer_id in previous_reviewers:
                if reviewer_id not in pr["pending_removals_review"]:
                    pr["pending_removals_review"].append(reviewer_id)
                    logger.info(f"Reviewer {reviewer_id} marked for re-review in PR {pr_id}")
                
                if 'pinged_users_redo_pending' not in pr:
                    pr['pinged_users_redo_pending'] = []
                
                pr['pinged_users_redo_pending'].append(reviewer_id)
                logger.info(f"Reviewer {reviewer_id} pinged for redo in PR {pr_id}")

            add_pr_to_store(pr)
            logger.info(f"PR {pr_id} updated in the store with pending reviewer pings.")

            # Update the PR modal view
            update_pr_modal_view(client, pr_id, pr, body["view"]["id"], logger)
            logger.info(f"PR modal view updated for PR {pr_id}")
        
        else:
            logger.error(f"PR with ID {pr_id} not found in the store")

    except Exception as e:
        logger.error(f"Error handling ping_previous_reviewers for PR {pr_id}: {e}")
