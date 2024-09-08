from listeners import actions
from listeners import commands
from listeners import events
from listeners import views


def register_listeners(app):
    actions.register(app)
    commands.register(app)
    events.register(app)
    views.register(app)