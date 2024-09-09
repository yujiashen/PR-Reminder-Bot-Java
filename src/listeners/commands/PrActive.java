package prreminderbot.actions;

import com.slack.api.bolt.context.builtin.ActionContext;
import com.slack.api.methods.SlackApiException;
import com.slack.api.Slack;
import org.slf4j.Logger;
import prreminderbot.database.Database;
import prreminderbot.database.DatabaseChannelSettings;
import prreminderbot.helpers.Helpers;
import prreminderbot.sla_check.SlaCheck;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class PrActive {

    public static String populateMessageText(int channelSlaTime, PriorityQueue<Map.Entry<Integer, String>> overduePrHeap,
                                             List<Map.Entry<Map<String, Object>, Integer>> nearSlaPrs, List<Map<String, Object>> activePrs,
                                             List<Map<String, Object>> reviewedPrs, Slack client, Logger logger) throws IOException, SlackApiException {
        logger.info("Generating message text for channel with SLA time: {}", channelSlaTime);
        StringBuilder messageText = new StringBuilder("*:bell: PR Review Reminder*\nHere's a summary of active PRs in this channel:\n\n");

        // Add overdue PRs to the message
        if (!overduePrHeap.isEmpty()) {
            messageText.append(":warning: *The following PRs are overdue for review*\n\n");
            while (!overduePrHeap.isEmpty()) {
                Map.Entry<Integer, String> overduePr = overduePrHeap.poll();
                Map<String, Object> pr = Database.getPrById(overduePr.getValue());
                String formattedTimeOverdue = SlaCheck.formatTimeOverdue(-overduePr.getKey());
                String submitterName = Helpers.getUsername(client, (String) pr.get("submitter_id"));
                String prStatus = Helpers.getStatus(pr);
                messageText.append(
                    String.format("• *<%s|%s>* by %s\n   - Overdue by %s\n   - _Status_: %s\n",
                        pr.get("permalink"), pr.get("name"), submitterName, formattedTimeOverdue, prStatus)
                );
            }
            if (!nearSlaPrs.isEmpty() || !activePrs.isEmpty() || !reviewedPrs.isEmpty()) {
                messageText.append("\n");
            }
        }

        // Add near-SLA PRs to the message
        if (!nearSlaPrs.isEmpty()) {
            messageText.append(":hourglass_flowing_sand: *The following PRs are within 1 hour of SLA*\n\n");
            for (Map.Entry<Map<String, Object>, Integer> nearSlaPr : nearSlaPrs) {
                Map<String, Object> pr = nearSlaPr.getKey();
                String submitterName = Helpers.getUsername(client, (String) pr.get("submitter_id"));
                String prStatus = Helpers.getStatus(pr);
                String formattedTimeUntilOverdue = SlaCheck.formatTimeUntilOverdue(nearSlaPr.getValue(), channelSlaTime);
                messageText.append(
                    String.format("• *<%s|%s>* by %s\n   - %s\n   - _Status_: %s\n",
                        pr.get("permalink"), pr.get("name"), submitterName, formattedTimeUntilOverdue, prStatus)
                );
            }
            if (!activePrs.isEmpty() || !reviewedPrs.isEmpty()) {
                messageText.append("\n");
            }
        }

        // Add active PRs that aren't overdue or near-SLA
        if (!activePrs.isEmpty()) {
            messageText.append(":scroll: *Active PRs*\n\n");
            for (Map<String, Object> pr : activePrs) {
                String submissionTime = LocalDateTime.parse((String) pr.get("timestamp")).format(DateTimeFormatter.ofPattern("MMM d, h:mm a"));
                String submitterName = Helpers.getUsername(client, (String) pr.get("submitter_id"));
                String prStatus = Helpers.getStatus(pr);
                messageText.append(
                    String.format("• *<%s|%s>* by %s\n   - Submitted %s\n   - _Status_: %s\n",
                        pr.get("permalink"), pr.get("name"), submitterName, submissionTime, prStatus)
                );
            }
        }

        // Add reviewed PRs
        if (!reviewedPrs.isEmpty()) {
            messageText.append(":white_check_mark: *Recently Reviewed PRs*\n");
            messageText.append("Please check if they've been merged and remove them to keep things tidy.\n");
            for (Map<String, Object> pr : reviewedPrs) {
                String submitterName = Helpers.getUsername(client, (String) pr.get("submitter_id"));
                messageText.append(
                    String.format("• *<%s|%s>* by %s\n",
                        pr.get("permalink"), pr.get("name"), submitterName)
                );
            }
        }

        logger.info("Message text generated successfully");
        return messageText.toString();
    }

    public static void populateSla(List<Map<String, Object>> prs, int channelSlaTime, Logger logger,
                                   PriorityQueue<Map.Entry<Integer, String>> overduePrHeap, List<Map.Entry<Map<String, Object>, Integer>> nearSlaPrs,
                                   List<Map<String, Object>> activePrs, List<Map<String, Object>> reviewedPrs) {
        LocalDateTime now = LocalDateTime.of(2024, 9, 6, 16, 40);
        logger.info("Populating SLA data at {}", now);

        for (Map<String, Object> pr : prs) {
            String prId = (String) pr.get("id");
            LocalDateTime prTimestamp = LocalDateTime.parse((String) pr.get("timestamp"));
            int timeElapsed = SlaCheck.calculateWorkingHours(prTimestamp, now);
            boolean reviewsNeeded = (int) pr.get("reviews_received") < (int) pr.get("reviews_needed");

            if (timeElapsed > channelSlaTime * 3600 && reviewsNeeded) {
                int timeOverdueSeconds = timeElapsed - channelSlaTime * 3600;
                overduePrHeap.add(Map.entry(-timeOverdueSeconds, prId));
            } else if ((channelSlaTime - 1) * 3600 <= timeElapsed && timeElapsed <= channelSlaTime * 3600 && reviewsNeeded) {
                nearSlaPrs.add(Map.entry(pr, timeElapsed));
            } else if (!reviewsNeeded) {
                reviewedPrs.add(pr);
            } else {
                activePrs.add(pr);
            }
        }

        logger.info("SLA data populated: {} overdue, {} near SLA, {} active, {} reviewed",
            overduePrHeap.size(), nearSlaPrs.size(), activePrs.size(), reviewedPrs.size());
    }

    public static void handlePrActiveCallback(ActionRequest req, ActionContext ctx, Slack client, Logger logger) {
        try {
            ctx.ack();
            String channelId = req.getPayload().getChannel().getId();
            logger.info("PR active callback invoked for channel {}", channelId);

            // Fetch PRs for the channel from the database
            List<Map<String, Object>> prs = Database.getChannelPrs(channelId);
            if (prs.isEmpty()) {
                client.methods().chatPostMessage(r -> r.channel(channelId).text("No active PRs found for this channel."));
                logger.info("No active PRs found for channel {}", channelId);
                return;
            }

            int channelSlaTime = DatabaseChannelSettings.getChannelSlaTime(channelId);
            logger.info("Channel SLA time for channel {}: {} hours", channelId, channelSlaTime);

            // Prepare to populate SLA data
            PriorityQueue<Map.Entry<Integer, String>> overduePrHeap = new PriorityQueue<>();
            List<Map.Entry<Map<String, Object>, Integer>> nearSlaPrs = new ArrayList<>();
            List<Map<String, Object>> activePrs = new ArrayList<>();
            List<Map<String, Object>> reviewedPrs = new ArrayList<>();

            populateSla(prs, channelSlaTime, logger, overduePrHeap, nearSlaPrs, activePrs, reviewedPrs);

            // Generate the message text
            String messageText = populateMessageText(channelSlaTime, overduePrHeap, nearSlaPrs, activePrs, reviewedPrs, client, logger);

            // Send the message to the channel
            if (!messageText.isEmpty()) {
                client.methods().chatPostMessage(r -> r
                    .channel(channelId)
                    .text(messageText)
                    .unfurlLinks(false)
                );
                logger.info("PR summary message posted successfully to channel {}", channelId);
            }

        } catch (IOException | SlackApiException e) {
            logger.error("Error sending message to channel {}: {}", req.getPayload().getChannel().getId(), e.getMessage());
        }
    }
}
