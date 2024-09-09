package prreminderbot.actions;

import com.slack.api.bolt.context.builtin.CommandContext;
import com.slack.api.methods.SlackApiException;
import com.slack.api.Slack;
import com.slack.api.model.block.Blocks;
import com.slack.api.model.block.element.BlockElements;
import com.slack.api.model.view.Views;
import org.slf4j.Logger;

import java.io.IOException;

public class SubmitPrCallback {

    public static void handleSubmitPrCallback(CommandRequest command, CommandContext ctx, Slack client, Logger logger) {
        try {
            ctx.ack();
            String channelId = command.getPayload().getChannelId();
            String userId = command.getPayload().getUserId();
            logger.info("Opening PR submission modal for channel {} by user {}", channelId, userId);

            client.methods().viewsOpen(r -> r
                .triggerId(command.getPayload().getTriggerId())
                .view(Views.view(view -> view
                    .type("modal")
                    .callbackId("handle_submit_pr")
                    .privateMetadata(channelId)
                    .title(vt -> vt.type("plain_text").text("Submit PR for Review"))
                    .blocks(List.of(
                        Blocks.input(input -> input
                            .blockId("pr_name_block")
                            .element(BlockElements.plainTextInput(pt -> pt.actionId("pr_name").placeholder(pl -> pl.type("plain_text").text("Enter the PR name"))))
                            .label(label -> label.type("plain_text").text("PR Name"))
                        ),
                        Blocks.input(input -> input
                            .blockId("pr_link_block")
                            .element(BlockElements.plainTextInput(pt -> pt.actionId("pr_link").placeholder(pl -> pl.type("plain_text").text("Enter the PR link"))))
                            .label(label -> label.type("plain_text").text("PR Link"))
                        ),
                        Blocks.input(input -> input
                            .blockId("pr_description_block")
                            .optional(true)
                            .element(BlockElements.plainTextInput(pt -> pt.actionId("pr_description").placeholder(pl -> pl.type("plain_text").text("Enter an optional description")).multiline(true)))
                            .label(label -> label.type("plain_text").text("Description (Optional)"))
                        ),
                        Blocks.input(input -> input
                            .blockId("reviews_needed_block")
                            .optional(true)
                            .element(BlockElements.plainTextInput(pt -> pt.actionId("reviews_needed").placeholder(pl -> pl.type("plain_text").text("Enter number of reviews needed (default is 2)"))))
                            .label(label -> label.type("plain_text").text("Reviews Needed"))
                        )
                    ))
                    .submit(v -> v.type("plain_text").text("Submit"))
                ))
            );

            logger.info("PR submission modal opened successfully for user {}.", userId);

        } catch (IOException | SlackApiException e) {
            logger.error("Error opening PR submission modal for user {}: {}", command.getPayload().getUserId(), e.getMessage());
            ctx.respond("There was an error opening the modal.");
        }
    }
}
