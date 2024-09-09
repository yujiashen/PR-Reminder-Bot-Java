import com.slack.api.bolt.context.builtin.ActionContext;
import com.slack.api.bolt.handler.builtin.ActionHandler;
import com.slack.api.methods.SlackApiException;
import com.slack.api.model.block.LayoutBlock;
import org.slf4j.Logger;
import prreminderbot.BlockHelpers;
import prreminderbot.Database;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class HandleAttentionRequest {

    public static void handle(ActionRequest req, ActionContext ctx, Slack client, Logger logger) {
        try {
            ctx.ack();  // Acknowledge the action

            String userId = req.getPayload().getUser().getId();
            String actionId = req.getPayload().getActions().get(0).getActionId();
            String prId = actionId.replace("attention_request_", "");
            logger.info("User {} is attempting to request attention for PR {}", userId, prId);

            Map<String, Object> pr = Database.getPrById(prId);

            if (pr != null) {
                List<String> attentionRequests = (List<String>) pr.get("attention_requests");

                if (attentionRequests.contains(userId)) {
                    attentionRequests.remove(userId);
                    logger.info("User {} removed their attention request from PR {}", userId, prId);
                } else {
                    attentionRequests.add(userId);
                    logger.info("User {} requested attention for PR {}", userId, prId);

                    String submitterId = (String) pr.get("submitter_id");
                    if (submitterId == null) {
                        logger.error("Submitter ID for PR {} not found.", prId);
                        return;
                    }

                    String userName = client.methods().usersInfo(r -> r.user(userId)).getUser().getRealName();
                    String originalPostTs = (String) pr.get("message_ts");

                    // Send a DM to the submitter
                    try {
                        client.methods().chatPostMessage(r -> r
                                .channel(submitterId)
                                .text("Attention requested by " + userName + " for your PR " + pr.get("name") + ".")
                                .blocks(List.of(
                                        LayoutBlock.section(section -> section
                                                .text(text -> text.type("mrkdwn").text(
                                                        "*Attention requested by " + userName + " for your PR* *<" +
                                                                pr.get("link") + "|" + pr.get("name") + ">*.\n" +
                                                                "<https://your-workspace.slack.com/archives/" +
                                                                pr.get("channel_id") + "/p" +
                                                                originalPostTs.replace(".", "") +
                                                                "|View original post>"
                                                ))
                                        )
                                ))
                                .unfurlLinks(false)
                        );
                        logger.info("Sent attention request notification to submitter {} for PR {}", submitterId, prId);
                    } catch (IOException | SlackApiException e) {
                        logger.error("Failed to send DM to submitter {} for PR {}: {}", submitterId, prId, e.getMessage());
                    }
                }

                // Update the attention_requests in the database
                Database.addPrToStore(pr);
                logger.info("PR {} attention requests updated in the store", prId);

                // Update the original message with the new blocks
                List<LayoutBlock> blocks = BlockHelpers.assemblePrMessageBlocks(client, pr, userId, logger);
                client.methods().chatUpdate(r -> r
                        .channel((String) pr.get("channel_id"))
                        .ts((String) pr.get("message_ts"))
                        .blocks(blocks)
                        .text(blocks.get(0).getText().getText())
                );
                logger.info("Updated PR message in channel {} for PR {}", pr.get("channel_id"), prId);

            } else {
                logger.error("PR {} not found in the store.", prId);
            }

        } catch (Exception e) {
            logger.error("Error handling attention request for PR {}: {}", req.getPayload().getActions().get(0).getActionId().replace("attention_request_", ""), e.getMessage());
        }
    }
}
