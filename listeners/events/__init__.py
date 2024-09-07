from slack_bolt import App
import re
from .app_home_opened import app_home_opened_callback
from .app_home_opened import handle_remove_pr_home


def register(app: App):
    app.event("app_home_opened")(app_home_opened_callback)
    app.action(re.compile(r"removePr_home_.*"))(handle_remove_pr_home)

