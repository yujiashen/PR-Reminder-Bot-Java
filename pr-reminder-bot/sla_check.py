from datetime import datetime, timedelta
import os
import heapq
from collections import defaultdict
from slack_sdk import WebClient
from database import get_prs_from_store, remove_pr_by_id, get_pr_by_id
from database_settings import get_channel_sla_time, get_channel_enabled_hours
from dotenv import load_dotenv

load_dotenv()

client = WebClient(token=os.environ['SLACK_BOT_TOKEN'])

convert_seconds = 3600

def is_within_working_hours(timestamp):
    """Check if the given timestamp is within 9 AM to 5 PM working hours."""
    return 9 <= timestamp.hour < 17 and timestamp.weekday() < 5  # Monday to Friday

def calculate_working_hours(start_time, end_time):
    """Calculate working hours between two times (9 AM to 5 PM, Monday to Friday)."""
    total_seconds = 0
    current = start_time

    while current < end_time:
        if is_within_working_hours(current):
            next_boundary = min(end_time, current.replace(hour=17, minute=0, second=0, microsecond=0))
            total_seconds += (next_boundary - current).total_seconds()

        current += timedelta(days=1)
        current = current.replace(hour=9, minute=0, second=0, microsecond=0)

    return total_seconds

def format_time_overdue(time_overdue_seconds):
    """Convert time overdue from seconds to hours and minutes."""
    minutes, seconds = divmod(time_overdue_seconds, 60)
    hours, minutes = divmod(minutes, 60)
    return f"{int(hours)} hours, {int(minutes)} minutes"

def is_pr_due_for_removal(pr, now):
    """Check if PR is older than 5 working days (excluding weekends)."""
    pr_timestamp = datetime.fromisoformat(pr["timestamp"])
    working_days = 0
    current = pr_timestamp

    while current < now:
        if current.weekday() < 5:  # Monday to Friday are working days
            working_days += 1
        current += timedelta(days=1)

    return working_days >= 5

def get_user_name(client, user_id):
    """Retrieve the user's name from their user ID."""
    try:
        user_info = client.users_info(user=user_id)
        return user_info['user']['real_name']
    except Exception as e:
        print(f"Error fetching user name for ID {user_id}: {e}")
        return "Unknown User"

def populate_sla(pr_store):
    now = datetime.now()
    prs_to_remove = []
    overdue_pr_heap = defaultdict(list)
    near_sla_prs = defaultdict(list)

    for pr in pr_store:
        pr_id = pr["id"]
        channel_id=pr["channel_id"]
        # Fetch the channel-specific SLA time and enabled hours
        channel_sla_time = get_channel_sla_time(channel_id)  # Default to 8 hours if not set
        
        # Remove PRs older than 5 working days
        if is_pr_due_for_removal(pr, now):
            print(f"PR {pr_id} is older than 5 working days. Removing it from the store.")
            client.chat_update(
                channel=pr["channel_id"],
                ts=pr["message_ts"],
                text=f"PR *<{pr['link']}|{pr['name']}>* has been automatically removed after 5 working days.",
                blocks=[]
            )
            prs_to_remove.append(pr_id)
            continue
        

        pr_timestamp = datetime.fromisoformat(pr["timestamp"])
        time_elapsed = calculate_working_hours(pr_timestamp-timedelta(hours=3), now)
        reviews_needed = pr["reviews_received"] < pr["reviews_needed"]

        # Check if the PR is overdue (SLA exceeded)
        # if reviews_needed and datetime.now() - pr_timestamp > timedelta(minutes=2):
        if time_elapsed > channel_sla_time * convert_seconds and reviews_needed:
            time_overdue_seconds = time_elapsed - int(channel_sla_time) * convert_seconds
            heapq.heappush(overdue_pr_heap[pr["channel_id"]], (-time_overdue_seconds, pr_id))
        # Check if the PR is within 1 hour of SLA
        elif (channel_sla_time-1) * convert_seconds <= time_elapsed <= channel_sla_time * convert_seconds and reviews_needed:
        # elif reviews_needed and timedelta(minutes=0) <= datetime.now() - pr_timestamp <= timedelta(minutes=2):
            near_sla_prs[pr["channel_id"]].append(pr)
    
    return prs_to_remove, overdue_pr_heap, near_sla_prs

def populate_message_text(channel_id, overdue_pr_heap, near_sla_prs):
    channel_sla_time = get_channel_sla_time(channel_id)
    
    message_text = "*:mega: PR Review Reminder*\n"
    message_text += f"SLA time for this channel: {channel_sla_time} hours\n"
    message_text += "Here's a summary of PRs that need your attention:\n\n"
    
    # Add overdue PRs to the message
    if channel_id in overdue_pr_heap:
        message_text += ":warning: *The following PRs are overdue for review:*\n"
        while overdue_pr_heap[channel_id]:
            time_overdue, pr_id = heapq.heappop(overdue_pr_heap[channel_id])
            pr = get_pr_by_id(pr_id)
            formatted_time_overdue = format_time_overdue(-time_overdue)  # Format the overdue time in hours and minutes
            submitter_name = get_user_name(client, pr['submitter_id'])
            message_text += f"• <{pr['permalink']}|{pr['name']}> by {submitter_name} - Overdue by {formatted_time_overdue}\n"  # Prevent unfurling

    # Add near-SLA PRs to the message
    if channel_id in near_sla_prs:
        if message_text:
            message_text += "\n"  # Separate the overdue and near-SLA sections
        
        message_text += ":hourglass_flowing_sand: *The following PRs are within 1 hour of SLA:*\n"
        for pr in near_sla_prs[channel_id]:
            submitter_name = get_user_name(client, pr['submitter_id'])
            message_text += f"• <{pr['permalink']}|{pr['name']}> by {submitter_name}\n"
    
    return message_text


def check_sla(client):
    """Check all PRs in the store for SLA violations and those within 1 hour of the SLA."""
    print(f"Running SLA check at {datetime.now()}")
    pr_store = get_prs_from_store()
    prs_to_remove, overdue_pr_heap, near_sla_prs = populate_sla(pr_store)

    # Remove PRs after the iteration is complete
    for pr_id in prs_to_remove:
        remove_pr_by_id(pr_id)

    # Notify about overdue PRs and near-SLA PRs, grouped by channel
    channels_to_notify = set(overdue_pr_heap.keys()).union(near_sla_prs.keys())
    
    for channel_id in channels_to_notify:
        # Check if the current time is within the enabled hours for the channel
        enabled_hours = get_channel_enabled_hours(channel_id)
        current_hour = datetime.now().hour
        if current_hour not in enabled_hours:
            continue  # Skip sending the message if outside enabled hours

        message_text = populate_message_text(channel_id, overdue_pr_heap, near_sla_prs)
        # Send the combined message to the channel
        if message_text:
            client.chat_postMessage(channel=channel_id, text=message_text, unfurl_links=False)
            print(f"Sent SLA notification for channel {channel_id}.")

if __name__ == "__main__":
    check_sla(client)