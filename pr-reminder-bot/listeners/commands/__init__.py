from slack_bolt import App
from .submit_pr import submit_pr_callback
from .pr_active import pr_active_callback
from .pr_settings import pr_settings_callback,handle_toggle_hour,handle_sla_time_input
import re


def register(app: App):
    app.command("/pr-submit")(submit_pr_callback)
    app.command("/pr-active")(pr_active_callback)
    app.command("/pr-settings")(pr_settings_callback)
    app.action(re.compile(r"toggle_hour_.*"))(handle_toggle_hour)
    app.event("sla_time_input_action")(handle_sla_time_input)
    app.view("pr_settings_modal")(handle_sla_time_input)