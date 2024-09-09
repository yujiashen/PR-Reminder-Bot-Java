package prreminderbot.actions;

import com.slack.api.bolt.context.builtin.EventContext;
import com.slack.api.methods.SlackApiException;
import com.slack.api.Slack;
import com.slack.api.model.block.Blocks;
import com.slack.api.model.block.composition.BlockCompositions;
import com.slack.api.model.view.Views;
import org.slf4j.Logger;
import prreminderbot.database.Database;
import prreminderbot.helpers.Helpers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AppHomeOpenedCallback {

    public static void handleAppHomeOpened(AppHomeOpenedEvent event, EventContext ctx, Slack client, Logger logger) {
        if (!event.getTab().equals("home")) {
            return;
        }

        try {
            String userId = event.getUser();
            logger.info("App home opened by user {}", userId);

            // Fetch the user's active PRs
            List<Map<String, Object>> activePrs = Database.getUserPrs(userId);
            List<com.slack.api.model.block.Block> blocks = new ArrayList<>();

            blocks.add(Blocks.section(section -> section.text(BlockCompositions.markdownText("*Welcome back, <@" + userId + ">!* :house:"))));
            blocks.add(Blocks.divider());
            blocks.add(Blocks.section(section -> section.text(BlockCompositions.markdownText("*Your Active PRs*"))));

            if (activePrs != null && !activePrs.isEmpty()) {
                logger.info("User {} has {} active PRs", userId, activePrs.size());
                for (Map<String, Object> pr : activePrs) {
                    String prStatus = Helpers.getStatus(pr);
                    String reviewersText = "";

                    if (pr.get("reviewers") != null) {
                        List<String> reviewers = (List<String>) pr.get("reviewers");
                        reviewersText = " - reviewed by " + String.join(", ", reviewers.stream().map(r -> "<@" + r + ">").toArray(String[]::new));
                    }

                    blocks.add(Blocks.section(section -> section.text(BlockCompositions.markdownText("• *<" + pr.get("permalink") + "|" + pr.get("name") + ">* - " + prStatus + reviewersText))));
                    blocks.add(Blocks.actions(actions -> actions.elements(List.of(
                        BlockElements.button(b -> b
                            .text(BlockCompositions.plainText("Remove"))
                            .style("danger")
                            .actionId("removePr_home_" + pr.get("id"))
                            .confirm(confirm -> confirm
                                .title(BlockCompositions.plainText("Remove this PR?"))
                                .text(BlockCompositions.markdownText("This action cannot be undone."))
                                .confirm(BlockCompositions.plainText("Yes, remove it"))
                                .deny(BlockCompositions.plainText("Cancel"))
                            )
                        )
                    ))));
                }
            } else {
                logger.info("No active PRs found for user {}", userId);
                blocks.add(Blocks.section(section -> section.text(BlockCompositions.markdownText("You have no active PRs."))));
            }

            // Publish the home view
            client.methods().viewsPublish(r -> r
                .userId(userId)
                .view(Views.view(view -> view.type("home").blocks(blocks)))
            );
            logger.info("App home view published successfully for user {}", userId);

        } catch (IOException | SlackApiException e) {
            logger.error("Error publishing home tab for user {}: {}", event.getUser(), e.getMessage());
        }
    }

    public static void handleRemovePrHome(ActionRequest req, EventContext ctx, Slack client, Logger logger) {
        try {
            String prId = req.getPayload().getActions().get(0).getActionId().split("_")[2];  // Extract PR ID
            String userId = req.getPayload().getUser().getId();
            handleRemovePr(req, ctx, client, logger);
            updateAppHome(client, userId, logger);
        } catch (IOException | SlackApiException e) {
            logger.error("Error removing PR: {}", e.getMessage());
        }
    }

    public static void updateAppHome(Slack client, String userId, Logger logger) throws IOException, SlackApiException {
        logger.info("Updating app home for user {}", userId);

        List<Map<String, Object>> activePrs = Database.getUserPrs(userId);
        List<com.slack.api.model.block.Block> blocks = new ArrayList<>();

        blocks.add(Blocks.section(section -> section.text(BlockCompositions.markdownText("*Welcome back, <@" + userId + ">!* :house:"))));
        blocks.add(Blocks.divider());
        blocks.add(Blocks.section(section -> section.text(BlockCompositions.markdownText("*Your Active PRs*"))));

        if (activePrs != null && !activePrs.isEmpty()) {
            logger.info("User {} has {} active PRs", userId, activePrs.size());
            for (Map<String, Object> pr : activePrs) {
                String prStatus = Helpers.getStatus(pr);
                String reviewersText = "";

                if (pr.get("reviewers") != null) {
                    List<String> reviewers = (List<String>) pr.get("reviewers");
                    reviewersText = " - reviewed by " + String.join(", ", reviewers.stream().map(r -> "<@" + r + ">").toArray(String[]::new));
                }

                blocks.add(Blocks.section(section -> section.text(BlockCompositions.markdownText("• *<" + pr.get("permalink") + "|" + pr.get("name") + ">* - " + prStatus + reviewersText))));
                blocks.add(Blocks.actions(actions -> actions.elements(List.of(
                    BlockElements.button(b -> b
                        .text(BlockCompositions.plainText("Remove"))
                        .style("danger")
                        .actionId("removePr_home_" + pr.get("id"))
                        .confirm(confirm -> confirm
                            .title(BlockCompositions.plainText("Remove this PR?"))
                            .text(BlockCompositions.markdownText("This action cannot be undone."))
                            .confirm(BlockCompositions.plainText("Yes, remove it"))
                            .deny(BlockCompositions.plainText("Cancel"))
                        )
                    )
                ))));
            }
        } else {
            logger.info("No active PRs found for user {}", userId);
            blocks.add(Blocks.section(section -> section.text(BlockCompositions.markdownText("You have no active PRs."))));
        }

        client.methods().viewsPublish(r -> r
            .userId(userId)
            .view(Views.view(view -> view.type("home").blocks(blocks)))
        );
        logger.info("App home view updated successfully for user {}", userId);
    }
}
