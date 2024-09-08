import os
from dotenv import load_dotenv
import logging
from slack_bolt import App
from slack_bolt.adapter.socket_mode import SocketModeHandler
from listeners import register_listeners
from database import setup_dynamodb
from database_channel_settings import setup_dynamodb_settings

load_dotenv()

logging.basicConfig(level=logging.WARNING)
logger = logging.getLogger(__name__)

app = App(token=os.environ.get("SLACK_BOT_TOKEN"))

register_listeners(app)

if __name__ == "__main__":
    setup_dynamodb_settings()
    setup_dynamodb()
    SocketModeHandler(app, os.environ.get("SLACK_APP_TOKEN")).start()