import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import com.slack.api.Slack;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.chat.ChatUpdateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SlaCheck {

    private static final Logger logger = LoggerFactory.getLogger(SlaCheck.class);
    private static final long CONVERT_SECONDS = 3600;

    public static boolean isWithinWorkingHours(LocalDateTime timestamp) {
        return timestamp.getHour() >= 9 && timestamp.getHour() < 17 && timestamp.getDayOfWeek().getValue() <= 5;
    }

    public static long calculateWorkingHours(LocalDateTime startTime, LocalDateTime endTime) {
        long totalSeconds = 0;
        LocalDateTime current = startTime;

        while (current.isBefore(endTime)) {
            if (isWithinWorkingHours(current)) {
                LocalDateTime nextBoundary = current.withHour(17).withMinute(0).withSecond(0).withNano(0);
                nextBoundary = nextBoundary.isAfter(endTime) ? endTime : nextBoundary;
                totalSeconds += Duration.between(current, nextBoundary).getSeconds();
            }
            current = current.plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);
        }
        return totalSeconds;
    }

    public static String formatTimeOverdue(long timeOverdueSeconds) {
        long minutes = (timeOverdueSeconds / 60) % 60;
        long hours = timeOverdueSeconds / 3600;
        return String.format("%d hours, %d minutes", hours, minutes);
    }

    public static String formatTimeUntilOverdue(long timeElapsedSeconds, int channelSlaTime) {
        long remainingSeconds = channelSlaTime * CONVERT_SECONDS - timeElapsedSeconds;
        long remainingMinutes = remainingSeconds / 60;
        return String.format("%d minutes until overdue", remainingMinutes);
    }

    public static boolean isPrDueForRemoval(Map<String, Object> pr, LocalDateTime now) {
        LocalDateTime prTimestamp = LocalDateTime.parse((String) pr.get("timestamp"));
        long workingDays = 0;
        LocalDateTime current = prTimestamp;

        while (current.isBefore(now)) {
            if (current.getDayOfWeek().getValue() <= 5) {
                workingDays++;
            }
            current = current.plusDays(1);
        }
        return workingDays >= 5;
    }

    public static Map<String, Object> populateSla(List<Map<String, Object>> prStore) {
        LocalDateTime now = LocalDateTime.now();
        List<String> prsToRemove = new ArrayList<>();
        Map<String, PriorityQueue<Map.Entry<Long, String>>> overduePrHeap = new HashMap<>();
        Map<String, List<Map.Entry<Map<String, Object>, Long>>> nearSlaPrs = new HashMap<>();

        for (Map<String, Object> pr : prStore) {
            String prId = (String) pr.get("id");
            String channelId = (String) pr.get("channel_id");
            int channelSlaTime = DatabaseChannelSettings.getChannelSlaTime(channelId);

            if (isPrDueForRemoval(pr, now)) {
                logger.info(String.format("PR %s is older than 5 working days. Removing it.", prId));
                Slack.getInstance().methods().chatUpdate(r -> r.channel(channelId).text("PR has been automatically removed after 5 working days."));
                prsToRemove.add(prId);
                continue;
            }

            LocalDateTime prTimestamp = LocalDateTime.parse((String) pr.get("timestamp"));
            long timeElapsed = calculateWorkingHours(prTimestamp, now);
            boolean reviewsNeeded = (int) pr.get("reviews_received") < (int) pr.get("reviews_needed");

            if (timeElapsed > channelSlaTime * CONVERT_SECONDS && reviewsNeeded) {
                long timeOverdueSeconds = timeElapsed - channelSlaTime * CONVERT_SECONDS;
                overduePrHeap.computeIfAbsent(channelId, k -> new PriorityQueue<>(Map.Entry.comparingByKey())).add(Map.entry(-timeOverdueSeconds, prId));
            } else if (timeElapsed >= (channelSlaTime - 1) * CONVERT_SECONDS && timeElapsed <= channelSlaTime * CONVERT_SECONDS && reviewsNeeded) {
                nearSlaPrs.computeIfAbsent(channelId, k -> new ArrayList<>()).add(Map.entry(pr, timeElapsed));
            }
        }

        return Map.of("prsToRemove", prsToRemove, "overduePrHeap", overduePrHeap, "nearSlaPrs", nearSlaPrs);
    }

    public static String populateMessageText(String channelId, Map<String, PriorityQueue<Map.Entry<Long, String>>> overduePrHeap, Map<String, List<Map.Entry<Map<String, Object>, Long>>> nearSlaPrs) {
        int channelSlaTime = DatabaseChannelSettings.getChannelSlaTime(channelId);
        StringBuilder messageText = new StringBuilder("*:mega: PR Review Reminder*\n");
        messageText.append(String.format("SLA time for this channel: %d hours\n", channelSlaTime));
        messageText.append("Here's a summary of PRs that need your attention:\n\n");

        PriorityQueue<Map.Entry<Long, String>> overduePrs = overduePrHeap.get(channelId);
        if (overduePrs != null && !overduePrs.isEmpty()) {
            messageText.append(":warning: *The following PRs are overdue for review*\n\n");
            while (!overduePrs.isEmpty()) {
                Map.Entry<Long, String> overduePr = overduePrs.poll();
                String prId = overduePr.getValue();
                Map<String, Object> pr = Database.getPrById(prId);
                String formattedTimeOverdue = formatTimeOverdue(-overduePr.getKey());
                String submitterName = Utils.getUsername(Slack.getInstance(), (String) pr.get("submitter_id"));
                String prStatus = Utils.getStatus(pr);
                messageText.append(String.format("• *<%s|%s>* by %s\n   - Overdue by %s\n   - _Status_: %s\n", pr.get("permalink"), pr.get("name"), submitterName, formattedTimeOverdue, prStatus));
            }
        }

        List<Map.Entry<Map<String, Object>, Long>> nearSlaList = nearSlaPrs.get(channelId);
        if (nearSlaList != null && !nearSlaList.isEmpty()) {
            messageText.append("\n:hourglass_flowing_sand: *The following PRs are within 1 hour of SLA*\n\n");
            for (Map.Entry<Map<String, Object>, Long> nearSla : nearSlaList) {
                Map<String, Object> pr = nearSla.getKey();
                String submitterName = Utils.getUsername(Slack.getInstance(), (String) pr.get("submitter_id"));
                String prStatus = Utils.getStatus(pr);
                String formattedTimeUntilOverdue = formatTimeUntilOverdue(nearSla.getValue(), channelSlaTime);
                messageText.append(String.format("• *<%s|%s>* by %s\n   - %s\n   - _Status_: %s\n", pr.get("permalink"), pr.get("name"), submitterName, formattedTimeUntilOverdue, prStatus));
            }
        }

        return messageText.toString();
    }

    public static void checkSla(Slack client) {
        LocalDateTime now = LocalDateTime.now();
        logger.info(String.format("Running SLA check at %s", now));
        int currentHour = now.getHour();
        List<Map<String, Object>> prStore = Database.getPrsFromStore();
        Map<String, Object> results = populateSla(prStore);

        List<String> prsToRemove = (List<String>) results.get("prsToRemove");
        Map<String, PriorityQueue<Map.Entry<Long, String>>> overduePrHeap = (Map<String, PriorityQueue<Map.Entry<Long, String>>>) results.get("overduePrHeap");
        Map<String, List<Map.Entry<Map<String, Object>, Long>>> nearSlaPrs = (Map<String, List<Map.Entry<Map<String, Object>, Long>>>) results.get("nearSlaPrs");

        for (String prId : prsToRemove) {
            Database.removePrById(prId);
        }

        Set<String> channelsToNotify = new HashSet<>(overduePrHeap.keySet());
        channelsToNotify.addAll(nearSlaPrs.keySet());

        for (String channelId : channelsToNotify) {
            List<Integer> enabledHours = DatabaseChannelSettings.getChannelEnabledHours(channelId);
            if (!enabledHours.contains(currentHour)) {
                logger.info(String.format("Skipping channel %s, current hour %d is not within enabled hours.", channelId, currentHour));
                continue;
            }

            String messageText = populateMessageText(channelId, overduePrHeap, nearSlaPrs);
            if (!messageText.isEmpty()) {
                try {
                    ChatPostMessageResponse response = client.methods().chatPostMessage(r -> r.channel(channelId).text(messageText).unfurlLinks(false));
                    logger.info(String.format("Sent SLA notification for channel %s.", channelId));
                } catch (Exception e) {
                    logger.error(String.format("Error sending SLA notification for channel %s: %s", channelId, e.getMessage()));
                }
            }
        }
    }

    public static void main(String[] args) {
        Slack client = Slack.getInstance();
        checkSla(client);
    }
}
