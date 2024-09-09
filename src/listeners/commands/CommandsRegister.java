package prreminderbot.actions;

import com.slack.api.bolt.App;
import org.slf4j.Logger;

public class CommandsRegistry {

    public static void register(App app, Logger logger) {

        // Register the /pr-submit command
        app.command("/pr-submit", (req, ctx) -> {
            SubmitPrCallback.handleSubmitPrCallback(req, ctx, app.slack(), logger);
            return ctx.ack();
        });

        // Register the /pr-active command
        app.command("/pr-active", (req, ctx) -> {
            PrActive.handlePrActiveCallback(req, ctx, app.slack(), logger);
            return ctx.ack();
        });

        // Register the /pr-settings command
        app.command("/pr-settings", (req, ctx) -> {
            PrSettingsCallback.handlePrSettingsCallback(req, ctx, app.slack(), logger);
            return ctx.ack();
        });

        // Register the toggle hour action
        app.action("toggle_hour_.*", (req, ctx) -> {
            PrSettingsCallback.handleToggleHour(req, ctx, app.slack(), logger);
            return ctx.ack();
        });

        // Register the SLA time input action
        app.viewSubmission("pr_settings_modal", (req, ctx) -> {
            PrSettingsCallback.handleSlaTimeInput(req, ctx, app.slack(), logger);
            return ctx.ack();
        });
    }
}
