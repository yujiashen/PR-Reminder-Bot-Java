
import heapq
from datetime import datetime
from slack_sdk import WebClient
from database import get_channel_prs, get_pr_by_id
from helpers import get_status
from sla_check import format_time_overdue, get_user_name, calculate_working_hours

SLA_hours = 8
convert_seconds = 120

def populate_message_text(channel_id, overdue_pr_heap, near_sla_prs, active_prs, client):
    message_text = "*:bell: PR Review Reminder*\n"
    message_text += "Here's a summary of active PRs in this channel:\n\n"

    # Add overdue PRs to the message
    if overdue_pr_heap:
        message_text += ":warning: *The following PRs are overdue for review:*\n"
        while overdue_pr_heap:
            time_overdue, pr_id = heapq.heappop(overdue_pr_heap)
            pr = get_pr_by_id(pr_id)
            formatted_time_overdue = format_time_overdue(-time_overdue)
            submitter_name = get_user_name(client, pr['submitter_id'])
            pr_status = get_status(pr)  # Get the status using get_status
            message_text += f"• <{pr['permalink']}|{pr['name']}> by {submitter_name} - Overdue by {formatted_time_overdue} - Status: {pr_status}\n"

    # Add near-SLA PRs to the message
    if near_sla_prs:
        if message_text:
            message_text += "\n"
        message_text += ":hourglass_flowing_sand: *The following PRs are within 1 hour of SLA:*\n"
        for pr in near_sla_prs:
            submitter_name = get_user_name(client, pr['submitter_id'])
            pr_status = get_status(pr)
            message_text += f"• <{pr['permalink']}|{pr['name']}> by {submitter_name} - Status: {pr_status}\n"

    # Add active PRs that aren't overdue or near-SLA
    if active_prs:
        if message_text:
            message_text += "\n"
        message_text += ":scroll: *Other Active PRs:*\n"
        for pr in active_prs:
            submitter_name = get_user_name(client, pr['submitter_id'])
            pr_status = get_status(pr)
            message_text += f"• <{pr['permalink']}|{pr['name']}> by {submitter_name} - Status: {pr_status}\n"

    return message_text


def populate_sla(prs, client):
    now = datetime.now()
    overdue_pr_heap = []  # Use a list for heap
    near_sla_prs = []
    active_prs = []
    
    for pr in prs:
        pr_id = pr["id"]
        pr_timestamp = datetime.fromisoformat(pr["timestamp"])
        time_elapsed = calculate_working_hours(pr_timestamp, now)
        reviews_needed = pr["reviews_received"] < pr["reviews_needed"]

        # Check if the PR is overdue (SLA exceeded)
        if time_elapsed > SLA_hours * convert_seconds and reviews_needed:
            time_overdue_seconds = time_elapsed - SLA_hours * convert_seconds
            heapq.heappush(overdue_pr_heap, (-time_overdue_seconds, pr_id))
        # Check if the PR is within 1 hour of SLA
        elif (SLA_hours - 1) * convert_seconds <= time_elapsed <= SLA_hours * convert_seconds and reviews_needed:
            near_sla_prs.append(pr)
        # If it's still active but not nearing SLA or overdue
        else:
            active_prs.append(pr)

    return overdue_pr_heap, near_sla_prs, active_prs


def pr_active_callback(ack, body, client, logger):
    ack()
    channel_id = body['channel_id']

    # Fetch PRs for the channel from DynamoDB
    prs = get_channel_prs(channel_id)

    if not prs:
        client.chat_postMessage(
            channel=channel_id,
            text="No active PRs found for this channel."
        )
        return

    # Populate the SLA data for this channel
    overdue_pr_heap, near_sla_prs, active_prs = populate_sla(prs, client)

    # Generate the message text
    message_text = populate_message_text(channel_id, overdue_pr_heap, near_sla_prs, active_prs, client)

    # Send the message to the channel
    if message_text:
        try:
            client.chat_postMessage(
                channel=channel_id,
                text=message_text,
                unfurl_links=False
            )
        except Exception as e:
            logger.error(f"Error sending message to channel {channel_id}: {e}")
