import com.slack.api.bolt.context.builtin.ActionContext;
import com.slack.api.bolt.handler.builtin.ActionHandler;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.views.ViewsUpdateResponse;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.element.BlockElement;
import com.slack.api.model.block.element.BlockElements;
import org.slf4j.Logger;
import prreminderbot.Database;

import java.io.IOException;
import java.util.*;

public class HandlePrActions {

    public static void handleRemoveReviewer(ActionRequest req, ActionContext ctx, Slack client, Logger logger) {
        try {
            ctx.ack();  // Acknowledge the action

            String actionId = req.getPayload().getActions().get(0).getActionId();
            String[] splitActionId = actionId.split("_");
            String reviewerId = splitActionId[1];
            String prId = splitActionId[2];

            logger.info("Attempting to remove reviewer {} from PR {}", reviewerId, prId);
            Map<String, Object> pr = Database.getPrById(prId);

            if (pr != null) {
                List<String> pendingRemovals = (List<String>) pr.getOrDefault("pending_removals_review", new ArrayList<>());
                if (!pendingRemovals.contains(reviewerId)) {
                    pendingRemovals.add(reviewerId);
                    logger.info("Reviewer {} marked for removal from PR {}", reviewerId, prId);
                }

                pr.put("pending_removals_review", pendingRemovals);
                Database.addPrToStore(pr);
                logger.info("PR {} updated in the store after marking reviewer {} for removal.", prId, reviewerId);

                updatePrModalView(client, prId, pr, req.getPayload().getView().getId(), logger);
            } else {
                logger.warn("PR {} not found when attempting to remove reviewer {}", prId, reviewerId);
            }
        } catch (Exception e) {
            logger.error("Error removing reviewer from PR: {}", e.getMessage());
        }
    }

    public static void handleRemoveAttention(ActionRequest req, ActionContext ctx, Slack client, Logger logger) {
        try {
            ctx.ack();  // Acknowledge the action

            String actionId = req.getPayload().getActions().get(0).getActionId();
            String[] splitActionId = actionId.split("_");
            String userId = splitActionId[1];
            String prId = splitActionId[2];

            logger.info("Attempting to remove attention request for user {} from PR {}", userId, prId);
            Map<String, Object> pr = Database.getPrById(prId);

            if (pr != null) {
                List<String> pendingRemovals = (List<String>) pr.getOrDefault("pending_removals_attention", new ArrayList<>());
                if (!pendingRemovals.contains(userId)) {
                    pendingRemovals.add(userId);
                    logger.info("User {} marked for attention removal from PR {}", userId, prId);
                }

                pr.put("pending_removals_attention", pendingRemovals);
                Database.addPrToStore(pr);
                logger.info("PR {} updated in the store after marking user {} for attention removal.", prId, userId);

                updatePrModalView(client, prId, pr, req.getPayload().getView().getId(), logger);
            } else {
                logger.warn("PR {} not found when attempting to remove attention for user {}", prId, userId);
            }
        } catch (Exception e) {
            logger.error("Error removing attention from PR: {}", e.getMessage());
        }
    }

    public static void handlePingAttention(ActionRequest req, ActionContext ctx, Slack client, Logger logger) {
        try {
            ctx.ack();  // Acknowledge the action

            String actionId = req.getPayload().getActions().get(0).getActionId();
            String[] splitActionId = actionId.split("_");
            String userId = splitActionId[1];
            String prId = splitActionId[2];

            logger.info("Pinging attention request for user {} in PR {}", userId, prId);
            Map<String, Object> pr = Database.getPrById(prId);

            if (pr != null) {
                List<String> pingedUsers = (List<String>) pr.getOrDefault("pinged_users_attention_pending", new ArrayList<>());
                pingedUsers.add(userId);
                pr.put("pinged_users_attention_pending", pingedUsers);

                List<String> pendingRemovals = (List<String>) pr.getOrDefault("pending_removals_attention", new ArrayList<>());
                if (!pendingRemovals.contains(userId)) {
                    pendingRemovals.add(userId);
                    logger.info("User {} marked for attention removal after ping in PR {}", userId, prId);
                }

                pr.put("pending_removals_attention", pendingRemovals);
                Database.addPrToStore(pr);
                logger.info("PR {} updated in the store after pinging user {} for attention.", prId, userId);

                updatePrModalView(client, prId, pr, req.getPayload().getView().getId(), logger);
            } else {
                logger.warn("PR {} not found when attempting to ping attention for user {}", prId, userId);
            }
        } catch (Exception e) {
            logger.error("Error pinging attention for PR: {}", e.getMessage());
        }
    }

    public static void updatePrModalView(Slack client, String prId, Map<String, Object> pr, String viewId, Logger logger) {
        try {
            Set<String> reviewersMinusPending = new HashSet<>(((Map<String, Object>) pr.get("reviewers")).keySet());
            reviewersMinusPending.removeAll((List<String>) pr.getOrDefault("pending_removals_review", new ArrayList<>()));

            Set<String> attentionMinusPending = new HashSet<>(((Map<String, Object>) pr.get("attention_requests")).keySet());
            attentionMinusPending.removeAll((List<String>) pr.getOrDefault("pending_removals_attention", new ArrayList<>()));

            List<LayoutBlock> reviewerBlocks = new ArrayList<>();
            for (String reviewerId : reviewersMinusPending) {
                reviewerBlocks.add(
                        Blocks.section(section -> section
                                .text(text -> text.type("mrkdwn").text("*" + client.methods().usersInfo(r -> r.user(reviewerId)).getUser().getRealName() + "*"))
                                .accessory(BlockElements.button(b -> b.text(pt -> pt.type("plain_text").text("Remove"))
                                        .style("danger").actionId("removeReviewer_" + reviewerId + "_" + prId)))
                        )
                );
            }

            if (!reviewersMinusPending.isEmpty()) {
                reviewerBlocks.add(Blocks.actions(actions -> actions
                        .elements(List.of(
                                BlockElements.button(b -> b
                                        .text(pt -> pt.type("plain_text").text("Ping previous reviewers to redo +1"))
                                        .actionId("ping_previous_reviewers_button_" + prId)
                                        .value(prId)
                                )
                        ))
                ));
            }

            List<LayoutBlock> attentionBlocks = new ArrayList<>();
            for (String userId : attentionMinusPending) {
                attentionBlocks.add(Blocks.section(section -> section.text(text -> text.type("mrkdwn").text("<@" + userId + ">"))));
                attentionBlocks.add(Blocks.actions(actions -> actions.elements(List.of(
                        BlockElements.button(b -> b.text(pt -> pt.type("plain_text").text("Ping & Remove"))
                                .style("primary").actionId("pingAttention_" + userId + "_" + prId).value(userId)),
                        BlockElements.button(b -> b.text(pt -> pt.type("plain_text").text("Remove"))
                                .style("danger").actionId("removeAttention_" + userId + "_" + prId))
                ))));
            }

            List<LayoutBlock> blocks = new ArrayList<>(List.of(
                    Blocks.input(input -> input
                            .blockId("pr_name_block")
                            .label(label -> label.type("plain_text").text("PR Name"))
                            .element(BlockElements.plainTextInput(inputEl -> inputEl.actionId("pr_name").initialValue((String) pr.get("name"))))),
                    Blocks.input(input -> input
                            .blockId("pr_link_block")
                            .label(label -> label.type("plain_text").text("PR Link"))
                            .element(BlockElements.plainTextInput(inputEl -> inputEl.actionId("pr_link").initialValue((String) pr.get("link"))))),
                    Blocks.input(input -> input
                            .blockId("pr_description_block")
                            .label(label -> label.type("plain_text").text("PR Description"))
                            .optional(true)
                            .element(BlockElements.plainTextInput(inputEl -> inputEl.actionId("pr_description").initialValue((String) pr.get("description"))))),
                    Blocks.input(input -> input
                            .blockId("reviews_needed_block")
                            .label(label -> label.type("plain_text").text("Reviews Needed"))
                            .optional(true)
                            .element(BlockElements.plainTextInput(inputEl -> inputEl.actionId("reviews_needed").initialValue(String.valueOf(pr.get("reviews_needed")))))
                    ),
                    Blocks.divider(),
                    Blocks.section(section -> section.text(text -> text.type("mrkdwn").text("*Reviewers*"))),
                    Blocks.divider()
            ));

            blocks.addAll(reviewerBlocks);
            blocks.add(Blocks.divider());
            blocks.add(Blocks.section(section -> section.text(text -> text.type("mrkdwn").text("*Attention Requested*"))));
            blocks.addAll(attentionBlocks);

            ViewsUpdateResponse response = client.methods().viewsUpdate(r -> r
                    .viewId(viewId)
                    .view(view -> view.type("modal")
                            .callbackId("edit_submit_modal")
                            .privateMetadata(prId)
                            .title(title -> title.type("plain_text").text("Edit PR"))
                            .blocks(blocks)
                            .submit(submit -> submit.type("plain_text").text("Save Changes"))
                    )
            );
            if (response.isOk()) {
                logger.info("PR modal view updated successfully for PR {}.", prId);
            } else {
                logger.error("Error updating PR modal view for PR {}: {}", prId, response.getError());
            }
        } catch (IOException | SlackApiException e) {
            logger.error("Error updating PR modal view for PR {}: {}", prId, e.getMessage());
        }
    }
}
