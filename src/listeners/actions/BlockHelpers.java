import com.slack.api.Slack;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.users.UsersInfoResponse;
import com.slack.api.model.block.Blocks;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.element.BlockElement;
import com.slack.api.model.block.element.BlockElements;
import com.slack.api.model.block.element.ButtonElement;
import org.slf4j.Logger;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BlockHelpers {

    public static List<LayoutBlock> assemblePrMessageBlocks(Slack client, Map<String, Object> pr, String userId, Logger logger) {
        try {
            List<LayoutBlock> blocks = createPrMessageBlock(client, pr, logger);
            logger.info("Assembling PR message blocks for PR {} by user {}", pr.get("id"), userId);

            // Append the necessary buttons
            blocks.get(1).getElements().add(createPlusOneButton((String) pr.get("id"), userId, pr));
            blocks.get(1).getElements().add(createQuestionMarkButton((String) pr.get("id"), userId, pr));
            blocks.get(1).getElements().add(createRemoveButton((String) pr.get("id")));
            blocks.get(1).getElements().add(createEditButton((String) pr.get("id")));

            return blocks;
        } catch (Exception e) {
            logger.error("Error assembling message blocks for PR {}: {}", pr.get("id"), e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public static List<LayoutBlock> createPrMessageBlock(Slack client, Map<String, Object> pr, Logger logger) {
        try {
            UsersInfoResponse submitterInfo = client.methods().usersInfo(r -> r.user((String) pr.get("submitter_id")));
            String submitterName = submitterInfo.getUser().getRealName();
            String submissionTime = LocalDateTime.parse((String) pr.get("timestamp"))
                    .format(DateTimeFormatter.ofPattern("MMM d, h:mm a"));

            List<String> reviewersText = new ArrayList<>();
            for (String reviewerId : (List<String>) pr.get("reviewers")) {
                UsersInfoResponse reviewerInfo = client.methods().usersInfo(r -> r.user(reviewerId));
                reviewersText.add("*" + reviewerInfo.getUser().getRealName() + "*");
            }
            String reviewsNeeded = String.valueOf((int) pr.get("reviews_needed") - (int) pr.get("reviews_received"));

            String messageText = ":memo: *PR Reviews Requested!*\n\n" +
                    "*" + pr.get("link") + "|" + pr.get("name") + "*\n" +
                    pr.get("description") + "\n\n" +
                    ":bust_in_silhouette: *Submitted by:* *" + submitterName + "* at " + submissionTime + "\n";

            if (Integer.parseInt(reviewsNeeded) <= 0) {
                messageText += "\n\n:white_check_mark: Your PR was reviewed by " + String.join(", ", reviewersText) +
                        "!\nIt’s ready to be merged.\nPlease merge the PR and remove it from the queue, or update the request if needed.";
            } else {
                messageText += "\n:hourglass_flowing_sand: Needs " + reviewsNeeded + " more reviews\n\n" +
                        ":white_check_mark: *+1s:* " + String.join(", ", reviewersText);
            }

            if (!((List<String>) pr.get("attention_requests")).isEmpty()) {
                List<String> attentionRequestedBy = new ArrayList<>();
                for (String userId : (List<String>) pr.get("attention_requests")) {
                    UsersInfoResponse attentionRequesterInfo = client.methods().usersInfo(r -> r.user(userId));
                    attentionRequestedBy.add("*" + attentionRequesterInfo.getUser().getRealName() + "*");
                }
                messageText += "\n\n:speech_balloon: *Attention requested by:* " + String.join(", ", attentionRequestedBy);
            }

            return List.of(
                    Blocks.section(section -> section.text(text -> text.type("mrkdwn").text(messageText))),
                    Blocks.actions(actions -> actions.elements(new ArrayList<>())) // Placeholder for buttons
            );
        } catch (IOException | SlackApiException e) {
            logger.error("Error creating PR message block for PR {}: {}", pr.get("id"), e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public static BlockElement createPlusOneButton(String prId, String userId, Map<String, Object> pr) {
        return BlockElements.button(b -> b
                .text(pt -> pt.type("plain_text").text("+1").emoji(true))
                .actionId("plus_one_" + prId)
                .value(prId)
        );
    }

    public static BlockElement createRemoveButton(String prId) {
        return BlockElements.button(b -> b
                .text(pt -> pt.type("plain_text").text("Remove"))
                .actionId("remove_pr_" + prId)
                .value(prId)
                .confirm(confirm -> confirm
                        .title(title -> title.type("plain_text").text("Are you sure?"))
                        .text(text -> text.type("mrkdwn").text("This action cannot be undone."))
                        .confirm(c -> c.type("plain_text").text("Yes, remove it"))
                        .deny(d -> d.type("plain_text").text("Cancel"))
                )
        );
    }

    public static BlockElement createEditButton(String prId) {
        return BlockElements.button(b -> b
                .text(pt -> pt.type("plain_text").text("Edit"))
                .actionId("edit_pr_" + prId)
                .value(prId)
        );
    }

    public static BlockElement createQuestionMarkButton(String prId, String userId, Map<String, Object> pr) {
        ButtonElement questionMarkButton = BlockElements.button(b -> b
                .text(pt -> pt.type("plain_text").text(":speech_balloon:").emoji(true))
                .actionId("attention_request_" + prId)
                .value(prId)
        );
        if (!((List<String>) pr.get("attention_requests")).isEmpty()) {
            questionMarkButton.setStyle("danger");
        }
        return questionMarkButton;
    }
}
