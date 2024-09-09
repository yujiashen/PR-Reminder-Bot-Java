package prreminderbot.listeners.views.handlers;

import com.slack.api.bolt.context.builtin.ViewSubmissionContext;
import com.slack.api.methods.SlackApiException;
import com.slack.api.Slack;
import com.slack.api.model.block.Blocks;
import org.slf4j.Logger;
import prreminderbot.database.Database;
import prreminderbot.helpers.Helpers;
import prreminderbot.actions.block_helpers.BlockHelpers;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class HandleSubmitPr {

    public static void handleSubmitPr(ViewSubmissionRequest req, ViewSubmissionContext ctx, Slack client, Logger logger) {
        try {
            ViewState stateValues = req.getPayload().getView().getState().getValues();
            
            String prName = stateValues.get("pr_name_block").get("pr_name").getValue();
            String prLink = stateValues.containsKey("pr_link_block") 
                ? stateValues.get("pr_link_block").get("pr_link").getValue() 
                : req.getPayload().getPrivateMetadata();
            String prDescription = stateValues.get("pr_description_block").get("pr_description").getValue();
            String reviewsNeeded = stateValues.get("reviews_needed_block").get("reviews_needed").getValue();
            String channelId = req.getPayload().getPrivateMetadata();

            prLink = Helpers.ensureHttpScheme(prLink);
            String prId = prLink;

            if (!Helpers.isValidUrl(prLink)) {
                ctx.ack(r -> r.responseAction("errors").errors(Map.of("pr_link_block", "The PR link you entered is not valid. Please enter a valid URL.")));
                logger.warn("Invalid PR link provided: {}", prLink);
                return;
            }

            if (reviewsNeeded == null || reviewsNeeded.isEmpty()) {
                reviewsNeeded = "2";
            }
            if (!Helpers.isValidInt(reviewsNeeded)) {
                ctx.ack(r -> r.responseAction("errors").errors(Map.of("reviews_needed_block", "Please enter a valid number.")));
                logger.warn("Invalid reviews needed value: {}", reviewsNeeded);
                return;
            }
            int reviewsNeededInt = Integer.parseInt(reviewsNeeded);

            Map<String, Object> pr = Database.getPrById(prId);

            if (pr != null) {  // Existing PR
                ctx.ack(r -> r.responseAction("errors").errors(Map.of("pr_link_block", "A PR with this link already exists. Please use a different link.")));
                logger.warn("PR with ID {} already exists.", prId);
                return;
            } else {  // New PR
                ctx.ack();

                String submitterId = req.getPayload().getUser().getId();
                Map<String, Object> prInfo = new HashMap<>();
                prInfo.put("id", prId);
                prInfo.put("name", prName);
                prInfo.put("link", prLink);
                prInfo.put("description", prDescription);
                prInfo.put("reviews_needed", reviewsNeededInt);
                prInfo.put("reviews_received", 0);
                prInfo.put("timestamp", Instant.now().toString());
                prInfo.put("channel_id", channelId);
                prInfo.put("reviewers", new HashMap<String, Object>());
                prInfo.put("attention_requests", new HashMap<String, Object>());
                prInfo.put("pinged_users", new HashMap<String, Object>());
                prInfo.put("submitter_id", submitterId);

                var response = client.methods().chatPostMessage(r -> r
                    .channel(channelId)
                    .text("PR submitted: *<" + prLink + "|" + prName + ">*")
                    .blocks(BlockHelpers.assemblePrMessageBlocks(client, prInfo, submitterId, logger))
                    .unfurlLinks(false)
                );

                if (response.isOk()) {
                    prInfo.put("message_ts", response.getTs());

                    // Fetch the permalink for the PR message
                    var permalinkResponse = client.methods().chatGetPermalink(r -> r
                        .channel(channelId)
                        .messageTs(response.getTs())
                    );
                    if (permalinkResponse.isOk()) {
                        prInfo.put("permalink", permalinkResponse.getPermalink());
                    }

                    Database.addPrToStore(prInfo);
                    logger.info("PR {} submitted and stored successfully.", prId);
                } else {
                    logger.error("Failed to post message for PR {}. Response: {}", prId, response);
                }
            }

        } catch (IOException | SlackApiException e) {
            logger.error("Error during PR submission: {}", e.getMessage());
        }
    }
}
