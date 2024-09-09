package prreminderbot.listeners.views.handlers;

import com.slack.api.bolt.context.builtin.ViewSubmissionContext;
import com.slack.api.methods.SlackApiException;
import com.slack.api.model.block.Blocks;
import com.slack.api.model.view.ViewState;
import com.slack.api.Slack;
import org.slf4j.Logger;
import prreminderbot.database.Database;
import prreminderbot.helpers.Helpers;
import prreminderbot.actions.block_helpers.BlockHelpers;

import java.io.IOException;
import java.util.Map;

public class HandleSubmitEdit {

    public static void handleSubmitEdit(ViewSubmissionRequest req, ViewSubmissionContext ctx, Slack client, Logger logger) {
        try {
            ViewState stateValues = req.getPayload().getView().getState().getValues();

            String prName = stateValues.get("pr_name_block").get("pr_name").getValue();
            String prLink = req.getPayload().getPrivateMetadata();
            String prDescription = stateValues.get("pr_description_block").get("pr_description").getValue();
            String reviewsNeeded = stateValues.get("reviews_needed_block").get("reviews_needed").getValue();
            String prId = prLink;

            logger.info("Handling submit edit for PR {} with name {}", prId, prName);

            if (reviewsNeeded == null || reviewsNeeded.isEmpty()) {
                reviewsNeeded = "2";
            }
            if (!Helpers.isValidInt(reviewsNeeded)) {
                ctx.ack(r -> r.responseAction("errors").errors(Map.of("reviews_needed_block", "Please enter a valid number.")));
                logger.warn("Invalid reviews_needed input: {}", reviewsNeeded);
                return;
            }
            int reviewsNeededInt = Integer.parseInt(reviewsNeeded);

            Map<String, Object> pr = Database.getPrById(prId);

            if (pr != null) { // Existing PR
                ctx.ack();
                String userId = req.getPayload().getUser().getId();
                logger.info("Found existing PR {}, updating it.", prId);

                // Handle removals and pings
                Helpers.finalizeRemovalsAndPings(client, pr, logger);

                // Update PR fields
                pr.put("name", prName);
                pr.put("link", prLink);
                pr.put("description", prDescription);
                pr.put("reviews_needed", reviewsNeededInt);

                // Assemble and update the PR message
                List<com.slack.api.model.block.Block> blocks = BlockHelpers.assemblePrMessageBlocks(client, pr, userId, logger);
                client.methods().chatUpdate(r -> r
                    .channel((String) pr.get("channel_id"))
                    .ts((String) pr.get("message_ts"))
                    .blocks(blocks)
                    .text(Blocks.asBlocks(blocks.get(0)).get(0).getText().getText())
                );

                Database.addPrToStore(pr);
                logger.info("PR {} updated successfully.", prId);
            } else {
                ctx.ack(r -> r.responseAction("errors").errors(Map.of("pr_description_block", "PR not found.")));
                logger.error("PR with ID {} not found for editing.", prId);
            }

        } catch (IOException | SlackApiException e) {
            logger.error("Error during PR edit submission: {}", e.getMessage());
        }
    }
}
