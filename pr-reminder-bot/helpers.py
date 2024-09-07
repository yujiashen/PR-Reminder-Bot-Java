
def get_status(pr):
    reviews_needed = pr["reviews_needed"] - pr["reviews_received"]
    if len(pr['attention_requests']) > 0:
        pr_status = "attention needed"
    elif reviews_needed > 0:
        pr_status = f"needs {reviews_needed} reviews"
    elif reviews_needed == 0:
        pr_status = "PR reviewed!"
    return pr_status