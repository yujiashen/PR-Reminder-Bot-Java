package prreminderbot.actions;

import com.slack.api.bolt.context.builtin.ActionContext;
import com.slack.api.methods.SlackApiException;
import com.slack.api.Slack;
import org.slf4j.Logger;
import prreminderbot.database.Database;
import prreminderbot.block_helpers.BlockHelpers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HandlePlusOne {

    public static void handle(ActionRequest req, ActionContext ctx, Slack client, Logger logger) {
        try {
            ctx.ack();  // Acknowledge the action

            String userId = req.getPayload().getUser().getId();
            String actionId = req.getPayload().getActions().get(0).getActionId();
            String prId = actionId.replace("plus_one_", "");
            logger.info("User {} is attempting to +1 PR {}", userId, prId);

            Map<String, Object> pr = Database.getPrById(prId);

            if (pr != null) {
                Map<String, Boolean> reviewers = (Map<String, Boolean>) pr.get("reviewers");

                // Toggle the +1 status
                if (reviewers.containsKey(userId)) {
                    reviewers.remove(userId);
                    pr.put("reviews_received", (int) pr.get("reviews_received") - 1);
                    logger.info("User {} removed their +1 from PR {}", userId, prId);
                } else {
                    reviewers.put(userId, true);
                    pr.put("reviews_received", (int) pr.get("reviews_received") + 1);
                    logger.info("User {} added their +1 to PR {}", userId, prId);
                }

                Database.addPrToStore(pr);
                logger.info("PR {} updated in the store.", prId);

                // Fetch the updated list of reviewers
                List<String> reviewerNames = new ArrayList<>();
                for (String reviewer : reviewers.keySet()) {
                    try {
                        String realName = client.methods().usersInfo(r -> r.user(reviewer)).getUser().getRealName();
                        reviewerNames.add(realName);
                    } catch (IOException | SlackApiException e) {
                        logger.error("Failed to fetch user info for user ID {}: {}", reviewer, e.getMessage());
                    }
                }

                String reviewersText = reviewerNames.isEmpty() ? "None" : String.join(", ", reviewerNames);
                int reviewsNeeded = (int) pr.get("reviews_needed") - (int) pr.get("reviews_received");

                // Construct the main message text
                String messageText = "*" + pr.get("link") + "|" + pr.get("name") + "*\n" + pr.get("description");
                if (reviewsNeeded <= 0) {
                    messageText += (
                        "\n\n:white_check_mark: Your PR was reviewed by " + reviewersText + "!\n" +
                        "It’s ready to be merged.\n" +
                        "Please merge the PR and remove it from the queue, or update the request if needed."
                    );
                    client.methods().chatPostMessage(r -> r
                        .channel((String) pr.get("submitter_id"))
                        .text(":white_check_mark: Your PR *<" + pr.get("link") + "|" + pr.get("name") + ">* has been reviewed. Please remove it from queue or update the request.")
                        .blocks(List.of(
                            LayoutBlock.section(section -> section.text(text -> text
                                .type("mrkdwn")
                                .text(":white_check_mark: *Your PR* *<" + pr.get("link") + "|" + pr.get("name") + ">* *has been reviewed*.\n" +
                                      "Please remove it from the queue or update the request.\n" +
                                      "<https://your-workspace.slack.com/archives/" + pr.get("channel_id") +
                                      "/p" + ((String) pr.get("message_ts")).replace(".", "") + "|View original post>")
                            ))
                        ))
                        .unfurlLinks(false)
                    );
                    logger.info("PR {} has enough reviews and is ready to be merged.", prId);
                } else {
                    messageText += "\nNeeds " + reviewsNeeded + " more reviews\n\n*Reviewers:* " + reviewersText;
                }

                // Reconstruct the blocks with updated information
                List<LayoutBlock> blocks = BlockHelpers.assemblePrMessageBlocks(client, pr, userId, logger);

                // Update the original message with the new blocks
                client.methods().chatUpdate(r -> r
                    .channel((String) pr.get("channel_id"))
                    .ts((String) pr.get("message_ts"))
                    .blocks(blocks)
                    .text(messageText)
                );
                logger.info("Updated message for PR {} in channel {}.", prId, pr.get("channel_id"));

            } else {
                logger.warn("PR {} not found in the store.", prId);
            }

        } catch (IOException | SlackApiException e) {
            logger.error("Error in handle_plus_one for PR {}: {}", req.getPayload().getActions().get(0).getActionId().replace("plus_one_", ""), e.getMessage());
        }
    }
}
