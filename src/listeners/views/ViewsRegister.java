package prreminderbot.listeners.views;

import com.slack.api.bolt.App;
import org.slf4j.Logger;
import prreminderbot.listeners.views.handlers.HandleSubmitPr;
import prreminderbot.listeners.views.handlers.HandleSubmitEdit;

public class ViewsRegister {

    public static void register(App app, Logger logger) {

        // Register the handle_submit_pr action for modals
        app.view("handle_submit_pr", (req, ctx) -> {
            HandleSubmitPr.handleSubmitPr(req, ctx, app.slack(), logger);
            return ctx.ack();
        });

        // Register the handle_submit_edit action for modals
        app.view("edit_submit_modal", (req, ctx) -> {
            HandleSubmitEdit.handleSubmitEdit(req, ctx, app.slack(), logger);
            return ctx.ack();
        });
    }
}
