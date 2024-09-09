package prreminderbot.actions;

import com.slack.api.bolt.context.builtin.ActionContext;
import com.slack.api.methods.SlackApiException;
import com.slack.api.Slack;
import org.slf4j.Logger;
import prreminderbot.database.Database;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class HandlePingPreviousReviewers {

    public static void handle(ActionRequest req, ActionContext ctx, Slack client, Logger logger) {
        try {
            ctx.ack();  // Acknowledge the action

            String actionId = req.getPayload().getActions().get(0).getActionId();
            String prId = actionId.replace("ping_previous_reviewers_button_", "");
            logger.info("Handling ping for previous reviewers for PR {}", prId);

            Map<String, Object> pr = Database.getPrById(prId);

            // Fetch the PR details from the store
            if (pr != null) {
                List<String> previousReviewers = (List<String>) pr.getOrDefault("reviewers", List.of());
                List<String> pendingRemovalsReview = (List<String>) pr.getOrDefault("pending_removals_review", List.of());

                if (!previousReviewers.isEmpty()) {
                    logger.info("Found {} previous reviewers for PR {}", previousReviewers.size(), prId);
                } else {
                    logger.info("No previous reviewers found for PR {}", prId);
                }

                // Send a DM to each previous reviewer asking them to redo their +1
                for (String reviewerId : previousReviewers) {
                    if (!pendingRemovalsReview.contains(reviewerId)) {
                        pendingRemovalsReview.add(reviewerId);
                        logger.info("Reviewer {} marked for re-review in PR {}", reviewerId, prId);
                    }

                    List<String> pingedUsersRedoPending = (List<String>) pr.getOrDefault("pinged_users_redo_pending", List.of());
                    pingedUsersRedoPending.add(reviewerId);
                    pr.put("pinged_users_redo_pending", pingedUsersRedoPending);
                    logger.info("Reviewer {} pinged for redo in PR {}", reviewerId, prId);
                }

                pr.put("pending_removals_review", pendingRemovalsReview);
                Database.addPrToStore(pr);
                logger.info("PR {} updated in the store with pending reviewer pings.", prId);

                // Update the PR modal view
                UpdatePrModalView.update(client, prId, pr, req.getPayload().getView().getId(), logger);
                logger.info("PR modal view updated for PR {}", prId);

            } else {
                logger.error("PR with ID {} not found in the store.", prId);
            }

        } catch (IOException | SlackApiException e) {
            logger.error("Error handling ping_previous_reviewers for PR {}: {}", req.getPayload().getActions().get(0).getActionId().replace("ping_previous_reviewers_button_", ""), e.getMessage());
        }
    }
}
