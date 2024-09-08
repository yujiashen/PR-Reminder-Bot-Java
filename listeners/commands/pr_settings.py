from slack_sdk.errors import SlackApiError
from database_settings import get_channel_sla_time, get_channel_enabled_hours, set_channel_sla_time, toggle_channel_hour
from helpers import is_valid_int

def pr_settings_callback(ack, body, client, logger):
    ack()
    channel_id = body['channel_id']
    
    # Retrieve the current settings for this channel (SLA time and enabled hours)
    current_sla_time = get_channel_sla_time(channel_id)  # Default to 8 if not set
    enabled_hours = get_channel_enabled_hours(channel_id)  # Default to all enabled
    logger.info(f"PR settings callback invoked for channel {channel_id}. Current SLA time: {current_sla_time}, Enabled hours: {enabled_hours}")

    # Build the modal view
    modal_view = {
        "type": "modal",
        "callback_id": "pr_settings_modal",
        "private_metadata": channel_id,  # Pass channel_id to use later
        "title": {
            "type": "plain_text",
            "text": "PR Settings"
        },
        "submit": {
            "type": "plain_text",
            "text": "Save"
        },
        "close": {
            "type": "plain_text",
            "text": "Cancel"
        },
        "blocks": [
            {
                "type": "section",
                "text": {
                    "type": "mrkdwn",
                    "text": "*Configure settings for this channel:*"
                }
            },
            {
                "type": "input",
                "block_id": "sla_time_input",
                "element": {
                    "type": "plain_text_input",
                    "action_id": "sla_time_input_action",
                    "initial_value": str(current_sla_time),
                    "placeholder": {
                        "type": "plain_text",
                        "text": "Enter SLA Time in hours"
                    }
                },
                "label": {
                    "type": "plain_text",
                    "text": "SLA Time (in hours)"
                }
            },
            {
                "type": "section",
                "text": {
                    "type": "mrkdwn",
                    "text": "Configure SLA Check Hours PST (click to enable/disable):"
                }
            }
        ]
    }

    # Add buttons for each hour (9 AM to 4 PM PST)
    action_blocks = []
    for hour in range(9, 17):  # 9 AM to 4 PM
        format_12_hours = hour
        if format_12_hours > 12:
            format_12_hours -= 12
        button_block = {
            "type": "button",
            "text": {
                "type": "plain_text",
                "text": f"{format_12_hours}:00"
            },
            "action_id": f"toggle_hour_{hour}"
        }
        # Only set "primary" style if the hour is enabled
        if hour in enabled_hours:
            button_block["style"] = "primary"
        action_blocks.append(button_block)

    # Add action buttons in a row
    modal_view["blocks"].append({
        "type": "actions",
        "block_id": "hour_buttons",
        "elements": action_blocks
    })

    # Open the modal
    try:
        client.views_open(
            trigger_id=body['trigger_id'],
            view=modal_view
        )
        logger.info(f"Modal for PR settings opened successfully for channel {channel_id}")
    except SlackApiError as e:
        logger.error(f"Error opening modal for channel {channel_id}: {e.response['error']}")

def handle_sla_time_input(ack, body, client, logger):
    ack()
    
    # Extract channel_id from private_metadata
    channel_id = body['view']['private_metadata']
    
    # Extract SLA time input from the modal submission
    sla_time = body['view']['state']['values']['sla_time_input']['sla_time_input_action']['value']

    # Check if the SLA time is a valid integer
    if not is_valid_int(sla_time):
        ack(response_action="errors", errors={
            "sla_time_input": "Please enter an integer"
        })
        logger.warning(f"Invalid SLA time input for channel {channel_id}: {sla_time}")
        return
    
    try:
        # Store the new SLA time for this channel
        set_channel_sla_time(channel_id, sla_time)
        logger.info(f"SLA time for channel {channel_id} updated to {sla_time} hours.")
    except Exception as e:
        logger.error(f"Error updating SLA time for channel {channel_id}: {e}")

def handle_toggle_hour(ack, body, client, logger):
    # Acknowledge the button click and update the blocks dynamically
    ack(response_action="update", view={
        "type": body['view']['type'],
        "callback_id": body['view']['callback_id'],
        "title": body['view']['title'],
        "blocks": body['view']['blocks'],
        "private_metadata": body['view']['private_metadata'],
    })

    action_id = body['actions'][0]['action_id']
    hour = int(action_id.split("_")[-1])
    channel_id = body['view']['private_metadata']
    
    try:
        # Toggle the enabled state of the hour for the channel
        toggle_channel_hour(channel_id, hour)
        enabled_hours = get_channel_enabled_hours(channel_id)
        logger.info(f"Hour {hour}:00 toggled for channel {channel_id}. Enabled hours: {enabled_hours}")

        # Update the button style based on the new state
        for block in body['view']['blocks']:
            if block['block_id'] == "hour_buttons":
                for element in block['elements']:
                    if element['action_id'] == action_id:
                        if hour in enabled_hours:
                            element['style'] = "primary"  # Set the primary style if the hour is enabled
                        else:
                            # Remove the style attribute if the hour is disabled
                            if 'style' in element:
                                del element['style']
        
        # Respond with updated button states without re-opening the modal
        client.views_update(
            view_id=body['view']['id'],
            view={
                "type": body['view']['type'],
                "callback_id": body['view']['callback_id'],
                "title": body['view']['title'],
                "blocks": body['view']['blocks'],
                "private_metadata": body['view']['private_metadata'],
                "submit": {
                    "type": "plain_text",
                    "text": "Save"
                },
                "close": {
                    "type": "plain_text",
                    "text": "Cancel"
                }
            }
        )
        logger.info(f"View updated successfully for channel {channel_id}, hour {hour}:00.")
    except Exception as e:
        logger.error(f"Error toggling hour {hour} for channel {channel_id}: {e}")
