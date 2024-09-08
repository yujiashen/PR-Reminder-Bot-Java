import os
from slack_sdk import WebClient
import boto3
from datetime import datetime, timedelta
import random

client = WebClient(token=os.environ.get("SLACK_BOT_TOKEN"))

# Set the Moto server URL
MOTO_SERVER_URL = "http://localhost:5001"  # Point to the Moto server

def setup_dynamodb():
    """Set up the mock DynamoDB environment on the Moto server."""
    dynamodb = boto3.resource('dynamodb', region_name='us-west-2', endpoint_url=MOTO_SERVER_URL)

    # Create the PRs table if it doesn't exist
    table = dynamodb.create_table(
        TableName='PRs',
        KeySchema=[{'AttributeName': 'id', 'KeyType': 'HASH'}],
        AttributeDefinitions=[{'AttributeName': 'id', 'AttributeType': 'S'}],
        ProvisionedThroughput={'ReadCapacityUnits': 5, 'WriteCapacityUnits': 5}
    )

    # Wait until the table exists
    table.meta.client.get_waiter('table_exists').wait(TableName='PRs')
    return table

def get_dynamodb():
    """Get the DynamoDB resource, pointing to the Moto server."""
    return boto3.resource('dynamodb', region_name='us-west-2', endpoint_url=MOTO_SERVER_URL)

def add_pr_to_store(pr_info):
    table = get_dynamodb().Table('PRs')
    table.put_item(Item=pr_info)
    print(f"PR {pr_info['name']} has been added or updated in the store.")

def get_prs_from_store():
    table = get_dynamodb().Table('PRs')
    response = table.scan()
    return response.get('Items', [])

def get_pr_by_id(pr_id):
    table = get_dynamodb().Table('PRs')
    response = table.get_item(Key={'id': pr_id})
    return response.get('Item')

def remove_pr_by_id(pr_id):
    table = get_dynamodb().Table('PRs')
    table.delete_item(Key={'id': pr_id})
    print(f"PR {pr_id} has been removed from the store.")

def get_user_prs(user_id):
    """Get PRs created by the specific user."""
    table = get_dynamodb().Table('PRs')
    response = table.scan(
        FilterExpression=boto3.dynamodb.conditions.Attr('submitter_id').eq(user_id)
    )
    return response.get('Items', [])

def get_channel_prs(channel_id):
    """Get active PRs in a specific channel."""
    table = get_dynamodb().Table('PRs')
    response = table.scan(
        FilterExpression=boto3.dynamodb.conditions.Attr('channel_id').eq(channel_id)
    )
    return response.get('Items', [])

def delete_all_prs():
    """Delete all items from the PRs table."""
    table = get_dynamodb().Table('PRs')

    # Scan the table to get all items
    response = table.scan()
    items = response.get('Items', [])

    # Iterate over each item and delete it
    with table.batch_writer() as batch:
        for item in items:
            batch.delete_item(
                Key={'id': item['id']}
            )
    print(f"Deleted {len(items)} items from the PRs table.")

def simulate_sla_check():
    """Simulate SLA check by modifying timestamps of PRs in channel C07K93JGDRA and updating the database."""
    # Set 'now' to September 6th, 4:40 PM
    now = datetime(2024, 9, 6, 16, 40)
    
    # Variables for setting SLA time conditions
    overdue_hours = 5
    nearing_sla_hours = 2.5
    active_hours = 0
    
    # Get all PRs from the store
    prs = get_prs_from_store()

    # Channel ID to modify PRs for
    target_channel_id = 'C07K93JGDRA'
    
    # Filter PRs for the target channel
    pr_list = [pr for pr in prs if pr['channel_id'] == target_channel_id]

    # Helper function to slightly randomize the timedelta
    def randomize_timedelta(hours):
        """Add a small random delta to avoid exact timestamps."""
        random_minutes = random.randint(-10, 10)  # Randomize by up to 10 minutes
        return timedelta(hours=hours, minutes=random_minutes)

    # Ensure there are at least 4 PRs in the target channel
    if len(pr_list) >= 4:
        # Make 2 PRs overdue
        pr_list[0]['timestamp'] = datetime(2024, 9, 5, 16, 44).isoformat()
        pr_list[1]['timestamp'] = datetime(2024, 9, 6, 8, 20).isoformat()
        
        # Make 1 PR nearing SLA
        pr_list[2]['timestamp'] = datetime(2024, 9, 5, 10, 8).isoformat()
        
        # Make 1 PR active
        pr_list[3]['timestamp'] = datetime(2024, 9, 5, 12, 19).isoformat()

        # Save updated PRs back to the store
        for pr in pr_list:
            add_pr_to_store(pr)
    
    # Handle other PRs outside of the target channel if necessary
    for pr in prs:
        if pr['channel_id'] != target_channel_id:
            # Keep them active or modify based on your needs
            pr['timestamp'] = (now - randomize_timedelta(active_hours)).isoformat()
            add_pr_to_store(pr)