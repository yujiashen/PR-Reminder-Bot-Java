
from .block_helpers import assemble_pr_message_blocks
from .handle_plus_one import handle_plus_one
from .handle_remove_reviewer import handle_remove_reviewer, handle_remove_attention, handle_ping_attention
from .handle_remove_pr import handle_remove_pr
from .handle_edit_pr import handle_edit_pr
from .handle_attention_request import handle_attention_request
from .handle_ping_previous import handle_ping_previous_reviewers
import re

def register(app):
    app.action(re.compile(r"plus_one_.*"))(lambda ack, body, client, logger: handle_plus_one(ack, body, client, logger))
    app.action(re.compile(r"removeReviewer_.*"))(lambda ack, body, client, logger: handle_remove_reviewer(ack, body, client, logger))
    app.action(re.compile(r"removeAttention_.*"))(lambda ack, body, client, logger: handle_remove_attention(ack, body, client, logger))
    app.action(re.compile(r"pingAttention_.*"))(lambda ack, body, client, logger: handle_ping_attention(ack, body, client, logger))
    app.action(re.compile(r"remove_pr_.*"))(lambda ack, body, client, logger: handle_remove_pr(ack, body, client, logger))
    app.action(re.compile(r"edit_pr_.*"))(lambda ack, body, client, logger: handle_edit_pr(ack, body, client, logger))
    app.action(re.compile(r"attention_request_.*"))(lambda ack, body, client, logger: handle_attention_request(ack, body, client, logger))
    app.action(re.compile(r"ping_previous_reviewers_button_.*"))(lambda ack, body, client, logger: handle_ping_previous_reviewers(ack, body, client, logger))
