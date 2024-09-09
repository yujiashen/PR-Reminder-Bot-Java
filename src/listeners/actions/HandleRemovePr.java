package prreminderbot.actions;

import com.slack.api.bolt.context.builtin.ActionContext;
import com.slack.api.methods.SlackApiException;
import com.slack.api.Slack;
import org.slf4j.Logger;
import prreminderbot.database.Database;

import java.io.IOException;
import java.util.Map;

public class HandleRemovePr {

    public static void handle(ActionRequest req, ActionContext ctx, Slack client, Logger logger) {
        try {
            ctx.ack();  // Acknowledge the action

            String actionId = req.getPayload().getActions().get(0).getActionId();

            // Extract PR ID from the action ID
            String prId;
            if (actionId.startsWith("removePr_home")) {
                prId = actionId.replace("removePr_home_", "");
            } else {
                prId = actionId.replace("remove_pr_", "");
            }

            logger.info("Attempting to remove PR with ID {}", prId);

            // Get the PR from the database
            Map<String, Object> pr = Database.getPrById(prId);

            // Check if the PR exists in the store
            if (pr != null) {
                logger.info("PR {} found. Removing it from the review queue.", prId);

                // Inform the channel that the PR is being removed
                client.methods().chatUpdate(r -> r
                        .channel((String) pr.get("channel_id"))
                        .ts((String) pr.get("message_ts"))
                        .text("*<" + pr.get("link") + "|" + pr.get("name") + ">* has been removed from the review queue.")
                        .blocks(List.of(
                                LayoutBlock.section(section -> section.text(text -> text
                                        .type("mrkdwn")
                                        .text("*<" + pr.get("link") + "|" + pr.get("name") + ">* has been removed from the review queue.")
                                ))
                        ))
                );

                // Remove the PR from the database
                Database.removePrById(prId);
                logger.info("PR {} successfully removed from the store.", prId);
            } else {
                logger.warn("PR with ID {} not found in the store.", prId);
            }

        } catch (IOException | SlackApiException e) {
            logger.error("Error while removing PR {}: {}", req.getPayload().getActions().get(0).getActionId(), e.getMessage());
        }
    }
}
