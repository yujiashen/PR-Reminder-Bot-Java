package prreminderbot.actions;

import com.slack.api.bolt.context.builtin.ActionContext;
import com.slack.api.methods.SlackApiException;
import com.slack.api.Slack;
import com.slack.api.model.block.Blocks;
import com.slack.api.model.block.element.BlockElement;
import com.slack.api.model.block.element.BlockElements;
import com.slack.api.model.view.Views;
import org.slf4j.Logger;
import prreminderbot.database.DatabaseChannelSettings;
import prreminderbot.helpers.Helpers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PrSettingsCallback {

    public static void handlePrSettingsCallback(ActionRequest req, ActionContext ctx, Slack client, Logger logger) {
        try {
            ctx.ack();

            String channelId = req.getPayload().getChannel().getId();

            // Retrieve the current settings for this channel (SLA time and enabled hours)
            int currentSlaTime = DatabaseChannelSettings.getChannelSlaTime(channelId);  // Default to 8 if not set
            List<Integer> enabledHours = DatabaseChannelSettings.getChannelEnabledHours(channelId);  // Default to all enabled
            logger.info("PR settings callback invoked for channel {}. Current SLA time: {}, Enabled hours: {}", channelId, currentSlaTime, enabledHours);

            // Build the modal view
            List<BlockElement> actionBlocks = new ArrayList<>();
            for (int hour = 9; hour <= 16; hour++) {  // 9 AM to 4 PM
                int displayHour = hour > 12 ? hour - 12 : hour;
                BlockElement buttonBlock = BlockElements.button(b -> b
                    .text(pt -> pt.type("plain_text").text(displayHour + ":00"))
                    .actionId("toggle_hour_" + hour)
                );
                if (enabledHours.contains(hour)) {
                    buttonBlock = BlockElements.button(b -> b
                        .text(pt -> pt.type("plain_text").text(displayHour + ":00"))
                        .actionId("toggle_hour_" + hour)
                        .style("primary")  // Set "primary" style if the hour is enabled
                    );
                }
                actionBlocks.add(buttonBlock);
            }

            // Build the modal view blocks
            List<com.slack.api.model.block.Block> blocks = new ArrayList<>();
            blocks.add(Blocks.section(section -> section.text(text -> text.type("mrkdwn").text("*Configure settings for this channel:*"))));
            blocks.add(Blocks.input(input -> input
                .blockId("sla_time_input")
                .element(BlockElements.plainTextInput(ti -> ti.actionId("sla_time_input_action").initialValue(String.valueOf(currentSlaTime))))
                .label(label -> label.type("plain_text").text("SLA Time (in hours)"))
            ));
            blocks.add(Blocks.section(section -> section.text(text -> text.type("mrkdwn").text("*Configure SLA Check Hours PST (click to enable/disable)*"))));
            blocks.add(Blocks.actions(actions -> actions.blockId("hour_buttons").elements(actionBlocks)));

            // Open the modal
            client.methods().viewsOpen(r -> r
                .triggerId(req.getPayload().getTriggerId())
                .view(Views.view(view -> view
                    .type("modal")
                    .callbackId("pr_settings_modal")
                    .privateMetadata(channelId)
                    .title(vt -> vt.type("plain_text").text("PR Settings"))
                    .blocks(blocks)
                    .submit(s -> s.type("plain_text").text("Save"))
                    .close(c -> c.type("plain_text").text("Cancel"))
                ))
            );
            logger.info("Modal for PR settings opened successfully for channel {}", channelId);

        } catch (IOException | SlackApiException e) {
            logger.error("Error opening modal for channel {}: {}", req.getPayload().getChannel().getId(), e.getMessage());
        }
    }

    public static void handleSlaTimeInput(ActionRequest req, ActionContext ctx, Slack client, Logger logger) {
        try {
            ctx.ack();

            String channelId = req.getPayload().getView().getPrivateMetadata();
            String slaTime = req.getPayload().getView().getState().getValues().get("sla_time_input").get("sla_time_input_action").getValue();

            // Validate SLA time input
            if (!Helpers.isValidInt(slaTime)) {
                ctx.ack(r -> r.responseAction("errors").errors("sla_time_input", "Please enter a valid integer"));
                logger.warn("Invalid SLA time input for channel {}: {}", channelId, slaTime);
                return;
            }

            // Update the SLA time for the channel
            DatabaseChannelSettings.setChannelSlaTime(channelId, Integer.parseInt(slaTime));
            logger.info("SLA time for channel {} updated to {} hours.", channelId, slaTime);

        } catch (IOException | SlackApiException e) {
            logger.error("Error updating SLA time for channel {}: {}", req.getPayload().getView().getPrivateMetadata(), e.getMessage());
        }
    }

    public static void handleToggleHour(ActionRequest req, ActionContext ctx, Slack client, Logger logger) {
        try {
            ctx.ack(r -> r.responseAction("update"));

            String channelId = req.getPayload().getView().getPrivateMetadata();
            int hour = Integer.parseInt(req.getPayload().getActions().get(0).getActionId().split("_")[2]);

            // Toggle the enabled state of the hour for the channel
            DatabaseChannelSettings.toggleChannelHour(channelId, hour);
            List<Integer> enabledHours = DatabaseChannelSettings.getChannelEnabledHours(channelId);
            logger.info("Hour {}:00 toggled for channel {}. Enabled hours: {}", hour, channelId, enabledHours);

            // Update the view with the new button styles
            List<com.slack.api.model.block.Block> updatedBlocks = req.getPayload().getView().getBlocks();
            for (com.slack.api.model.block.Block block : updatedBlocks) {
                if (block instanceof com.slack.api.model.block.ActionsBlock) {
                    for (BlockElement element : ((com.slack.api.model.block.ActionsBlock) block).getElements()) {
                        if (((com.slack.api.model.block.element.ButtonElement) element).getActionId().equals("toggle_hour_" + hour)) {
                            if (enabledHours.contains(hour)) {
                                ((com.slack.api.model.block.element.ButtonElement) element).setStyle("primary");
                            } else {
                                ((com.slack.api.model.block.element.ButtonElement) element).setStyle(null);  // Remove the style if the hour is disabled
                            }
                        }
                    }
                }
            }

            // Update the view
            client.methods().viewsUpdate(r -> r
                .viewId(req.getPayload().getView().getId())
                .view(Views.view(view -> view
                    .type(req.getPayload().getView().getType())
                    .callbackId(req.getPayload().getView().getCallbackId())
                    .title(req.getPayload().getView().getTitle())
                    .blocks(updatedBlocks)
                    .privateMetadata(channelId)
                    .submit(s -> s.type("plain_text").text("Save"))
                    .close(c -> c.type("plain_text").text("Cancel"))
                ))
            );
            logger.info("View updated successfully for channel {}, hour {}:00.", channelId, hour);

        } catch (IOException | SlackApiException e) {
            logger.error("Error toggling hour {} for channel {}: {}", req.getPayload().getActions().get(0).getActionId().split("_")[2], req.getPayload().getView().getPrivateMetadata(), e.getMessage());
        }
    }
}
