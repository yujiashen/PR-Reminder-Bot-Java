import com.slack.api.bolt.App;
import java.util.regex.Pattern;
import prreminderbot.block_helpers.AssemblePrMessageBlocks;
import prreminderbot.handle_plus_one.HandlePlusOne;
import prreminderbot.handle_remove_reviewer.HandleRemoveReviewer;
import prreminderbot.handle_remove_reviewer.HandleRemoveAttention;
import prreminderbot.handle_remove_reviewer.HandlePingAttention;
import prreminderbot.handle_remove_pr.HandleRemovePr;
import prreminderbot.handle_edit_pr.HandleEditPr;
import prreminderbot.handle_attention_request.HandleAttentionRequest;
import prreminderbot.handle_ping_previous.HandlePingPreviousReviewers;
import org.slf4j.Logger;

public class ActionsRegister {

    public static void register(App app) {
        app.action(Pattern.compile("plus_one_.*"), (req, ctx) -> {
            HandlePlusOne.handle(req, ctx, ctx.client(), ctx.logger());
            return ctx.ack();
        });

        app.action(Pattern.compile("removeReviewer_.*"), (req, ctx) -> {
            HandleRemoveReviewer.handle(req, ctx, ctx.client(), ctx.logger());
            return ctx.ack();
        });

        app.action(Pattern.compile("removeAttention_.*"), (req, ctx) -> {
            HandleRemoveAttention.handle(req, ctx, ctx.client(), ctx.logger());
            return ctx.ack();
        });

        app.action(Pattern.compile("pingAttention_.*"), (req, ctx) -> {
            HandlePingAttention.handle(req, ctx, ctx.client(), ctx.logger());
            return ctx.ack();
        });

        app.action(Pattern.compile("remove_pr_.*"), (req, ctx) -> {
            HandleRemovePr.handle(req, ctx, ctx.client(), ctx.logger());
            return ctx.ack();
        });

        app.action(Pattern.compile("edit_pr_.*"), (req, ctx) -> {
            HandleEditPr.handle(req, ctx, ctx.client(), ctx.logger());
            return ctx.ack();
        });

        app.action(Pattern.compile("attention_request_.*"), (req, ctx) -> {
            HandleAttentionRequest.handle(req, ctx, ctx.client(), ctx.logger());
            return ctx.ack();
        });

        app.action(Pattern.compile("ping_previous_reviewers_button_.*"), (req, ctx) -> {
            HandlePingPreviousReviewers.handle(req, ctx, ctx.client(), ctx.logger());
            return ctx.ack();
        });
    }
}
