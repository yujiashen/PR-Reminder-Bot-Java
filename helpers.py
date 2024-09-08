
def get_status(pr):
    reviews_needed = pr["reviews_needed"] - pr["reviews_received"]
    if len(pr['attention_requests']) > 0:
        pr_status = "attention needed"
    elif reviews_needed > 0:
        pr_status = f"needs {reviews_needed} reviews"
    elif reviews_needed == 0:
        pr_status = "PR reviewed!"
    return pr_status

def get_username(client, user_id):
    """Retrieve the user's name from their user ID."""
    try:
        user_info = client.users_info(user=user_id)
        return user_info['user']['real_name']
    except Exception as e:
        print(f"Error fetching user name for ID {user_id}: {e}")
        return "Unknown User"
    
def is_valid_int(value: str) -> bool:
    """Check if the given value is a valid integer."""
    try:
        int(value)
        return True
    except ValueError:
        return False