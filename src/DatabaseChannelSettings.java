import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.model.*;
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec;
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DatabaseChannelSettings {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseChannelSettings.class);
    private static final String REGION = "us-west-2";
    private static final String MOTO_SERVER_URL = "http://localhost:5001";
    private static final String TABLE_NAME = "PRSettings";

    // Create the DynamoDB client pointing to Moto Server
    private static DynamoDB createDynamoDBClient() {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(MOTO_SERVER_URL, REGION))
                .build();
        return new DynamoDB(client);
    }

    // Set up the DynamoDB table for PR Settings
    public static Table setupDynamodbSettings() {
        DynamoDB dynamoDB = createDynamoDBClient();

        try {
            Table table = dynamoDB.getTable(TABLE_NAME);
            table.describe(); // Check if the table exists, it will throw ResourceNotFoundException if not
            logger.info("PRSettings table already exists.");
            return table;
        } catch (ResourceNotFoundException e) {
            logger.info("Creating PRSettings table...");

            CreateTableRequest request = new CreateTableRequest()
                    .withTableName(TABLE_NAME)
                    .withKeySchema(new KeySchemaElement("channel_id", KeyType.HASH))
                    .withAttributeDefinitions(new AttributeDefinition("channel_id", ScalarAttributeType.S))
                    .withProvisionedThroughput(new ProvisionedThroughput(5L, 5L));

            Table table = dynamoDB.createTable(request);

            try {
                table.waitForActive();
                logger.info("PRSettings table created successfully.");
                return table;
            } catch (InterruptedException ie) {
                logger.error("Error while waiting for the table to become active.", ie);
                throw new RuntimeException(ie);
            }
        } catch (Exception e) {
            logger.error("Error setting up DynamoDB for PRSettings", e);
            throw new RuntimeException(e);
        }
    }

    // Get the SLA time for a specific channel
    public static int getChannelSlaTime(String channelId) {
        Table table = createDynamoDBClient().getTable(TABLE_NAME);

        try {
            GetItemSpec spec = new GetItemSpec().withPrimaryKey("channel_id", channelId);
            Item item = table.getItem(spec);

            if (item != null) {
                int slaTime = item.getInt("sla_time");
                logger.info("SLA time for channel {} is {} hours.", channelId, slaTime);
                return slaTime;
            } else {
                logger.info("No SLA time set for channel {}, defaulting to 8 hours.", channelId);
                return 8; // Default to 8 hours
            }
        } catch (Exception e) {
            logger.error("Error retrieving SLA time for channel {}: {}", channelId, e.getMessage());
            return 8; // Default to 8 hours in case of an error
        }
    }

    // Set the SLA time for a specific channel
    public static void setChannelSlaTime(String channelId, int slaTime) {
        Table table = createDynamoDBClient().getTable(TABLE_NAME);

        try {
            UpdateItemSpec updateSpec = new UpdateItemSpec()
                    .withPrimaryKey("channel_id", channelId)
                    .withUpdateExpression("set sla_time = :s")
                    .withValueMap(new ValueMap().withNumber(":s", slaTime));

            table.updateItem(updateSpec);
            logger.info("SLA time for channel {} set to {} hours.", channelId, slaTime);
        } catch (Exception e) {
            logger.error("Error setting SLA time for channel {}: {}", channelId, e.getMessage());
        }
    }

    // Get the enabled SLA check hours for a specific channel
    public static List<Integer> getChannelEnabledHours(String channelId) {
        Table table = createDynamoDBClient().getTable(TABLE_NAME);
        try {
            GetItemSpec spec = new GetItemSpec().withPrimaryKey("channel_id", channelId);
            Item item = table.getItem(spec);

            // Default enabled hours are 9 AM to 4 PM PST
            List<Integer> defaultEnabledHours = List.of(9, 10, 11, 12, 13, 14, 15, 16);

            if (item != null && item.hasAttribute("enabled_hours")) {
                List<Number> hours = (List<Number>) item.getList("enabled_hours");
                List<Integer> enabledHours = hours.stream().map(Number::intValue).collect(Collectors.toList());
                logger.info("Enabled hours for channel {}: {}", channelId, enabledHours);
                return enabledHours;
            } else {
                logger.info("No enabled hours set for channel {}, defaulting to {}", channelId, defaultEnabledHours);
                return defaultEnabledHours;
            }
        } catch (Exception e) {
            logger.error("Error retrieving enabled hours for channel {}: {}", channelId, e.getMessage());
            return List.of(9, 10, 11, 12, 13, 14, 15, 16); // Default to all hours enabled
        }
    }

    // Toggle the enabled state of a specific hour for SLA checks
    public static void toggleChannelHour(String channelId, int hour) {
        Table table = createDynamoDBClient().getTable(TABLE_NAME);

        try {
            List<Integer> currentHours = getChannelEnabledHours(channelId);

            // Toggle the hour
            if (currentHours.contains(hour)) {
                currentHours.remove(Integer.valueOf(hour));
            } else {
                currentHours.add(hour);
            }

            UpdateItemSpec updateSpec = new UpdateItemSpec()
                    .withPrimaryKey("channel_id", channelId)
                    .withUpdateExpression("set enabled_hours = :hours")
                    .withValueMap(new ValueMap().withList(":hours", currentHours));

            table.updateItem(updateSpec);
            logger.info("Enabled hours for channel {} updated to {}.", channelId, currentHours);
        } catch (Exception e) {
            logger.error("Error toggling hour for channel {}: {}", channelId, e.getMessage());
        }
    }
}
