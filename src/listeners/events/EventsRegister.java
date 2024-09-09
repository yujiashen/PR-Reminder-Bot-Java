package prreminderbot.actions;

import com.slack.api.bolt.App;
import org.slf4j.Logger;

public class EventsRegister {

    public static void register(App app, Logger logger) {

        // Register the app_home_opened event
        app.event(AppHomeOpenedEvent.class, (req, ctx) -> {
            AppHomeOpenedCallback.handleAppHomeOpened(req, ctx, app.slack(), logger);
            return ctx.ack();
        });

        // Register the remove PR action on the app home
        app.action("removePr_home_.*", (req, ctx) -> {
            RemovePrHomeCallback.handleRemovePrHome(req, ctx, app.slack(), logger);
            return ctx.ack();
        });
    }
}
