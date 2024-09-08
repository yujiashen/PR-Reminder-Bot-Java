import os
import boto3
from boto3.dynamodb.conditions import Attr
from decimal import Decimal

# Set the Moto server URL
MOTO_SERVER_URL = "http://localhost:5001"

def setup_dynamodb_settings():
    """Set up the DynamoDB table for PR Settings on the Moto server."""
    dynamodb = boto3.resource('dynamodb', region_name='us-west-2', endpoint_url=MOTO_SERVER_URL)

    # Create the PRSettings table if it doesn't exist
    table = dynamodb.create_table(
        TableName='PRSettings',
        KeySchema=[{'AttributeName': 'channel_id', 'KeyType': 'HASH'}],
        AttributeDefinitions=[{'AttributeName': 'channel_id', 'AttributeType': 'S'}],
        ProvisionedThroughput={'ReadCapacityUnits': 5, 'WriteCapacityUnits': 5}
    )

    # Wait until the table exists
    table.meta.client.get_waiter('table_exists').wait(TableName='PRSettings')
    return table

def get_dynamodb():
    """Get the DynamoDB resource, pointing to the Moto server."""
    return boto3.resource('dynamodb', region_name='us-west-2', endpoint_url=MOTO_SERVER_URL)

def get_channel_sla_time(channel_id):
    """Get the SLA time for a specific channel. Default to 8 hours if not set."""
    table = get_dynamodb().Table('PRSettings')  # Replace 'PRSettings' with your actual table name
    try:
        # Fetch the channel's settings from DynamoDB
        response = table.get_item(Key={'channel_id': channel_id})
        if 'Item' in response:
            # Return the SLA time if it exists, otherwise default to 8 hours
            return int(response['Item'].get('sla_time', 8))
        else:
            # Default SLA time is 8 hours if no settings exist for the channel
            return 8
    except Exception as e:
        print(f"Error retrieving SLA time for channel {channel_id}: {e}")
        return 8  # Default to 8 hours in case of an error

def set_channel_sla_time(channel_id, sla_time):
    """Set the SLA time for a specific channel."""
    table = get_dynamodb().Table('PRSettings')
    table.update_item(
        Key={'channel_id': channel_id},
        UpdateExpression="SET sla_time = :sla",
        ExpressionAttributeValues={':sla': int(sla_time)}
    )
    print(f"SLA time for channel {channel_id} set to {sla_time} hours.")

def get_channel_enabled_hours(channel_id):
    """Get the enabled SLA check hours for a specific channel (default all hours enabled)."""
    table = get_dynamodb().Table('PRSettings')
    response = table.get_item(Key={'channel_id': channel_id})
    
    # Default enabled hours are [9, 10, 11, 12, 13, 14, 15, 16] (9 AM to 4 PM PST)
    hours = response.get('Item', {}).get('enabled_hours', [9, 10, 11, 12, 13, 14, 15, 16])
    
    # Convert Decimal to int if needed
    return [int(hour) if isinstance(hour, Decimal) else hour for hour in hours]

def toggle_channel_hour(channel_id, hour):
    """Toggle the enabled state of a specific hour for SLA checks in a specific channel."""
    table = get_dynamodb().Table('PRSettings')
    
    # Get the current enabled hours
    current_hours = get_channel_enabled_hours(channel_id)
    
    # Toggle the hour
    if hour in current_hours:
        current_hours.remove(hour)
    else:
        current_hours.append(hour)
    
    # Update the enabled hours in the database
    table.update_item(
        Key={'channel_id': channel_id},
        UpdateExpression="SET enabled_hours = :hours",
        ExpressionAttributeValues={':hours': current_hours}
    )
    print(f"Enabled hours for channel {channel_id} updated to {current_hours}.")
