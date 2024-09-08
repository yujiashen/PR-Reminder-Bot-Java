import heapq
from datetime import datetime
from database import get_channel_prs, get_pr_by_id
from database_settings import get_channel_sla_time
from helpers import get_status, get_username
from sla_check import format_time_overdue, format_time_until_overdue, get_username, calculate_working_hours

def populate_message_text(channel_sla_time, overdue_pr_heap, near_sla_prs, active_prs, reviewed_prs, client, logger):
    logger.info(f"Generating message text for channel with SLA time: {channel_sla_time}")
    message_text = "*:bell: PR Review Reminder*\n"
    message_text += "Here's a summary of active PRs in this channel:\n\n"

    # Add overdue PRs to the message
    if overdue_pr_heap:
        message_text += ":warning: *The following PRs are overdue for review*\n\n"
        while overdue_pr_heap:
            time_overdue, pr_id = heapq.heappop(overdue_pr_heap)
            pr = get_pr_by_id(pr_id)
            formatted_time_overdue = format_time_overdue(-time_overdue)
            submitter_name = get_username(client, pr['submitter_id'])
            pr_status = get_status(pr)  # Get the status using get_status
            message_text += (
                f"• *<{pr['permalink']}|{pr['name']}>* by {submitter_name}\n"
                f"   - Overdue by {formatted_time_overdue}\n"
                f"   - _Status_: {pr_status}\n"
            )
        if near_sla_prs or active_prs or reviewed_prs:
            message_text += "\n"
    # Add near-SLA PRs to the message
    if near_sla_prs:
        message_text += ":hourglass_flowing_sand: *The following PRs are within 1 hour of SLA*\n\n"
        for pr, time_elapsed in near_sla_prs:
            submitter_name = get_username(client, pr['submitter_id'])
            pr_status = get_status(pr)
            formatted_time_until_overdue = format_time_until_overdue(time_elapsed, channel_sla_time)
            message_text += (
                f"• *<{pr['permalink']}|{pr['name']}>* by {submitter_name}\n"
                f"   - {formatted_time_until_overdue}\n"
                f"   - _Status_: {pr_status}\n"
            )
        if active_prs or reviewed_prs:
            message_text += "\n"
    # Add active PRs that aren't overdue or near-SLA
    if active_prs:
        if overdue_pr_heap or near_sla_prs:
            message_text += ":scroll: *Other Active PRs*\n\n"
        else:
            message_text += ":scroll: *Active PRs*\n\n"

        for pr in active_prs:
            submission_time = datetime.fromisoformat(pr['timestamp']).strftime('%b %-d, %-I:%M %p')
            submitter_name = get_username(client, pr['submitter_id'])
            pr_status = get_status(pr)
            message_text += (
                f"• *<{pr['permalink']}|{pr['name']}>* by {submitter_name}\n"
                f"   - Submitted {submission_time}\n"
                f"   - _Status_: {pr_status}\n"
            )
        if reviewed_prs:
            message_text += "\n"
            
    if reviewed_prs:
        message_text += ":white_check_mark: *Recently Reviewed PRs*\n"
        message_text += "Please check if they've been merged and remove them to keep things tidy. They will be automatically removed after 5 days.\n"
        for pr in reviewed_prs:
            submitter_name = get_username(client, pr['submitter_id'])
            message_text += f"• *<{pr['permalink']}|{pr['name']}>* by {submitter_name}\n"

    logger.info("Message text generated successfully")
    return message_text


def populate_sla(prs, channel_sla_time, logger):
    now = datetime(2024, 9, 6, 16, 40)
    logger.info(f"Populating SLA data at {now}")
    overdue_pr_heap = []  # Use a list for heap
    near_sla_prs = []
    active_prs = []
    reviewed_prs = []
    
    for pr in prs:
        pr_id = pr["id"]
        pr_timestamp = datetime.fromisoformat(pr["timestamp"])
        time_elapsed = calculate_working_hours(pr_timestamp, now)
        reviews_needed = pr["reviews_received"] < pr["reviews_needed"]
        SLA_hours = channel_sla_time
        # Check if the PR is overdue (SLA exceeded)
        if time_elapsed > SLA_hours * 3600 and reviews_needed:
            time_overdue_seconds = time_elapsed - SLA_hours * 3600
            heapq.heappush(overdue_pr_heap, (-time_overdue_seconds, pr_id))
        # Check if the PR is within 1 hour of SLA
        elif (SLA_hours - 1) * 3600 <= time_elapsed <= SLA_hours * 3600 and reviews_needed:
            near_sla_prs.append((pr, time_elapsed))
        elif not reviews_needed:
            reviewed_prs.append(pr)
        else:
            active_prs.append(pr)

    logger.info(f"SLA data populated: {len(overdue_pr_heap)} overdue, {len(near_sla_prs)} near SLA, {len(active_prs)} active, {len(reviewed_prs)} reviewed")
    return overdue_pr_heap, near_sla_prs, active_prs, reviewed_prs


def pr_active_callback(ack, body, client, logger):
    ack()
    channel_id = body['channel_id']
    logger.info(f"PR active callback invoked for channel {channel_id}")

    # Fetch PRs for the channel from DynamoDB
    prs = get_channel_prs(channel_id)
    if not prs:
        client.chat_postMessage(
            channel=channel_id,
            text="No active PRs found for this channel."
        )
        logger.info(f"No active PRs found for channel {channel_id}")
        return

    channel_sla_time = get_channel_sla_time(channel_id)
    logger.info(f"Channel SLA time for channel {channel_id}: {channel_sla_time} hours")

    # Populate the SLA data for this channel
    overdue_pr_heap, near_sla_prs, active_prs, reviewed_prs = populate_sla(prs, channel_sla_time, logger)

    # Generate the message text
    message_text = populate_message_text(channel_sla_time, overdue_pr_heap, near_sla_prs, active_prs, reviewed_prs, client, logger)

    # Send the message to the channel
    if message_text:
        try:
            client.chat_postMessage(
                channel=channel_id,
                text=message_text,
                unfurl_links=False
            )
            logger.info(f"PR summary message posted successfully to channel {channel_id}")
        except Exception as e:
            logger.error(f"Error sending message to channel {channel_id}: {e}")
