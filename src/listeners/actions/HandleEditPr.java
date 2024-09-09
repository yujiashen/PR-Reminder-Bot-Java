package prreminderbot.actions;

import com.slack.api.bolt.context.builtin.ActionContext;
import com.slack.api.methods.SlackApiException;
import com.slack.api.Slack;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.element.BlockElement;
import com.slack.api.model.block.element.BlockElements;
import org.slf4j.Logger;
import prreminderbot.database.Database;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HandleEditPr {

    public static void handle(ActionRequest req, ActionContext ctx, Slack client, Logger logger) {
        try {
            ctx.ack();  // Acknowledge the action

            String userId = req.getPayload().getUser().getId();
            String actionId = req.getPayload().getActions().get(0).getActionId();
            String prId = actionId.replace("edit_pr_", "");
            logger.info("User {} is attempting to edit PR {}", userId, prId);

            Map<String, Object> pr = Database.getPrById(prId);

            if (pr != null) {
                logger.info("PR {} found. Proceeding with edit.", prId);

                // Check if the user is the submitter of the PR
                if (!userId.equals(pr.get("submitter_id"))) {
                    // If the user is not the submitter, deny access
                    client.methods().chatPostEphemeral(r -> r
                        .channel((String) pr.get("channel_id"))
                        .user(userId)
                        .text("Only the PR submitter can edit this PR.")
                    );
                    logger.warn("User {} is not the submitter of PR {}. Edit access denied.", userId, prId);
                    return;
                }

                String prDescription = pr.get("description") != null ? (String) pr.get("description") : "";

                // Create the list of reviewers with remove buttons
                List<LayoutBlock> reviewerBlocks = new ArrayList<>();
                for (String reviewerId : (List<String>) pr.get("reviewers")) {
                    String reviewerName = client.methods().usersInfo(r -> r.user(reviewerId)).getUser().getRealName();
                    reviewerBlocks.add(Blocks.section(section -> section
                        .text(text -> text.type("mrkdwn").text("*" + reviewerName + "*"))
                        .accessory(BlockElements.button(b -> b
                            .text(pt -> pt.type("plain_text").text("Remove"))
                            .style("danger")
                            .actionId("removeReviewer_" + reviewerId + "_" + prId)
                        ))
                    ));
                }

                // Add the "Review updated, ping previous reviewers" button only if there are reviewers
                if (!((List<String>) pr.get("reviewers")).isEmpty()) {
                    reviewerBlocks.add(Blocks.actions(actions -> actions.elements(List.of(
                        BlockElements.button(b -> b
                            .text(pt -> pt.type("plain_text").text("Review updated, ping previous reviewers to redo +1"))
                            .style("primary")
                            .actionId("ping_previous_reviewers_button_" + prId)
                            .value(prId)
                        )
                    ))));
                }

                // Create the list of attention requests with ping and remove buttons
                List<LayoutBlock> attentionBlocks = new ArrayList<>();
                for (String attentionId : (List<String>) pr.get("attention_requests")) {
                    attentionBlocks.add(Blocks.section(section -> section
                        .text(text -> text.type("mrkdwn").text("<@" + attentionId + ">"))
                    ));
                    attentionBlocks.add(Blocks.actions(actions -> actions.elements(List.of(
                        BlockElements.button(b -> b
                            .text(pt -> pt.type("plain_text").text("Ping & Remove"))
                            .style("primary")
                            .actionId("pingAttention_" + attentionId + "_" + prId)
                            .value(attentionId)
                        ),
                        BlockElements.button(b -> b
                            .text(pt -> pt.type("plain_text").text("Remove"))
                            .style("danger")
                            .actionId("removeAttention_" + attentionId + "_" + prId)
                        )
                    ))));
                }

                // Open the modal with the current PR data for editing
                client.methods().viewsOpen(r -> r
                    .triggerId(req.getPayload().getTriggerId())
                    .view(view -> view
                        .type("modal")
                        .callbackId("edit_submit_modal")
                        .privateMetadata(prId)
                        .title(title -> title.type("plain_text").text("Edit PR"))
                        .blocks(List.of(
                            Blocks.input(input -> input
                                .blockId("pr_name_block")
                                .label(label -> label.type("plain_text").text("PR Name"))
                                .element(BlockElements.plainTextInput(pi -> pi.actionId("pr_name").initialValue((String) pr.get("name"))))
                            ),
                            Blocks.section(section -> section
                                .blockId("pr_link_block")
                                .text(text -> text.type("mrkdwn").text("*PR Link:* " + pr.get("link")))
                            ),
                            Blocks.input(input -> input
                                .blockId("pr_description_block")
                                .label(label -> label.type("plain_text").text("PR Description"))
                                .optional(true)
                                .element(BlockElements.plainTextInput(pi -> pi.actionId("pr_description").initialValue(prDescription)))
                            ),
                            Blocks.input(input -> input
                                .blockId("reviews_needed_block")
                                .label(label -> label.type("plain_text").text("Reviews Needed"))
                                .optional(true)
                                .element(BlockElements.plainTextInput(pi -> pi.actionId("reviews_needed").initialValue(String.valueOf(pr.get("reviews_needed")))))
                            ),
                            Blocks.divider(),
                            Blocks.section(section -> section.text(text -> text.type("mrkdwn").text("*Reviewers*"))),
                            Blocks.divider()
                        ))
                        .blocks(reviewerBlocks)
                        .blocks(List.of(
                            Blocks.divider(),
                            Blocks.section(section -> section.text(text -> text.type("mrkdwn").text("*Attention Requested*"))),
                            Blocks.divider()
                        ))
                        .blocks(attentionBlocks)
                        .submit(submit -> submit.type("plain_text").text("Save Changes"))
                    )
                );
                logger.info("Opened edit modal for PR {} for user {}", prId, userId);

            } else {
                logger.warn("PR {} not found in the store.", prId);
            }

        } catch (IOException | SlackApiException e) {
            logger.error("Error handling edit PR for PR {}: {}", req.getPayload().getActions().get(0).getActionId().replace("edit_pr_", ""), e.getMessage());
        }
    }
}
