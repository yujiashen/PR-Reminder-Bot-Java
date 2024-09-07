import os
from slack_sdk import WebClient
import boto3

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