from datetime import datetime
from urllib.parse import urlparse
from ..actions.block_helpers import assemble_pr_message_blocks
import re
from database import get_pr_by_id, add_pr_to_store

def ensure_http_scheme(url: str) -> str:
    """Ensure the URL has an http or https scheme, defaulting to https if missing."""
    parsed_url = urlparse(url)
    lower_url = url.lower()
    if not parsed_url.scheme:
        return f"https://{lower_url}"
    return lower_url

def is_valid_url(url: str) -> bool:
    """Check if the given URL is valid, ensuring it has a scheme and a proper domain."""
    url = ensure_http_scheme(url)
    try:
        result = urlparse(url)
        if not result.netloc or not re.match(r'^([a-zA-Z0-9-]+\.)+[a-zA-Z]{2,}$', result.netloc):
            return False
        return result.scheme in ["http", "https"]
    except ValueError:
        return False

def is_valid_int(value: str) -> bool:
    """Check if the given value is a valid integer."""
    try:
        int(value)
        return True
    except ValueError:
        return False

def handle_submit_pr(ack, body, client, logger):
    try:
        pr_name = body["view"]["state"]["values"]["pr_name_block"]["pr_name"]["value"]
        if "pr_link_block" in body["view"]["state"]["values"]:
            pr_link = body["view"]["state"]["values"]["pr_link_block"]["pr_link"]["value"]
        else:
            pr_link = body['view']['private_metadata']
        pr_description = body["view"]["state"]["values"]["pr_description_block"]["pr_description"]["value"] or ""
        reviews_needed = body["view"]["state"]["values"]["reviews_needed_block"]["reviews_needed"]["value"]
        channel_id = body['view']['private_metadata']

        pr_link = ensure_http_scheme(pr_link)
        pr_id = pr_link

        if not is_valid_url(pr_link):
            ack(response_action="errors", errors={
                "pr_link_block": "The PR link you entered is not valid. Please enter a valid URL."
            })
            return
        
        if not reviews_needed:
            reviews_needed = 2

        if not is_valid_int(reviews_needed):
            ack(response_action="errors", errors={
                "reviews_needed_block": "Please enter a valid number."
            })
            return

        reviews_needed = int(reviews_needed) if reviews_needed else 2

        pr = get_pr_by_id(pr_id)
        
        if pr: # Existing PR
            ack(response_action="errors", errors={
                "pr_link_block": "A PR with this link already exists. Please use a different link."
            })
            return
        else: # New PR
            ack()
            submitter_id = body["user"]["id"]
            pr_info = {
                "id": pr_id,
                "name": pr_name,
                "link": pr_link,
                "description": pr_description,
                "reviews_needed": reviews_needed,
                "reviews_received": 0,
                "timestamp": datetime.now().isoformat(),
                "channel_id": channel_id,
                "reviewers": {},
                "attention_requests": {},
                "pinged_users": {},
                "submitter_id": submitter_id,
            }

            response = client.chat_postMessage(
                channel=channel_id,
                text=f"PR submitted: *<{pr_link}|{pr_name}>*",
                blocks=assemble_pr_message_blocks(client, pr_info, user_id=submitter_id),
                unfurl_links = False
            )

            # Check if the response is successful
            if response.get("ok"):
                pr_info['message_ts'] = response["ts"]
                # Fetch permalink for the PR message
                permalink_response = client.chat_getPermalink(
                    channel=channel_id,
                    message_ts=response["ts"]
                )
                if permalink_response.get("ok"):
                    permalink = permalink_response["permalink"]
                    pr_info['permalink'] = permalink
                
                add_pr_to_store(pr_info)
                print(f"PR {pr_id} stored successfully.", get_pr_by_id(pr_id))
            else:
                print(f"Failed to post message for PR {pr_id}. Response: {response}")

    except Exception as e:
        logger.error(e)

