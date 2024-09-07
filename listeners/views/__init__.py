from slack_bolt import App
from listeners.views.handle_submit_pr import handle_submit_pr
from listeners.views.handle_submit_edit import handle_submit_edit
import re

def register(app):
    # Register actions for modals
    app.view("handle_submit_pr")(lambda ack, body, client, logger: handle_submit_pr(ack, body, client, logger))
    app.view("edit_submit_modal")(lambda ack, body, client, logger: handle_submit_edit(ack, body, client, logger))