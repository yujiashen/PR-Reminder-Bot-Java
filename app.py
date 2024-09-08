import os
from dotenv import load_dotenv
import logging
from threading import Thread
from slack_bolt import App
from slack_bolt.adapter.socket_mode import SocketModeHandler
from listeners import register_listeners
from database import setup_dynamodb, delete_all_prs, get_prs_from_store
from database_settings import setup_dynamodb_settings
# from sla_check import schedule_sla_check, run_scheduler
load_dotenv()

logging.basicConfig(level=logging.WARNING)
logger = logging.getLogger(__name__)

app = App(token=os.environ.get("SLACK_BOT_TOKEN"))

register_listeners(app)

# Run the app and stop Moto server after execution
if __name__ == "__main__":
    setup_dynamodb_settings()
    setup_dynamodb()
    # pr_store = get_prs_from_store()
    # print(pr_store)
    SocketModeHandler(app, os.environ.get("SLACK_APP_TOKEN")).start()