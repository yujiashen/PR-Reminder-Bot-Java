import com.slack.api.bolt.App;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ScanOutcome;
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec;
import com.amazonaws.services.dynamodbv2.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Database {

    private static final Logger logger = LoggerFactory.getLogger(Database.class);
    private static final String REGION = "us-west-2"; // Define your region
    private static final String MOTO_SERVER_URL = "http://localhost:5001"; // Define the Moto server URL
    private static final String TABLE_NAME = "PRs";

    // Create the DynamoDB client pointing to Moto Server
    private static DynamoDB createDynamoDBClient() {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(MOTO_SERVER_URL, REGION))
                .build();
        return new DynamoDB(client);
    }

    // Set up the DynamoDB table
    public static Table setupDynamodb() {
        DynamoDB dynamoDB = createDynamoDBClient();

        try {
            Table table = dynamoDB.getTable(TABLE_NAME);
            table.describe(); // Check if the table exists, it will throw ResourceNotFoundException if not.
            logger.info("PRs table already exists.");
            return table;
        } catch (ResourceNotFoundException e) {
            logger.info("Creating PRs table...");

            CreateTableRequest request = new CreateTableRequest()
                    .withTableName(TABLE_NAME)
                    .withKeySchema(new KeySchemaElement("id", KeyType.HASH))
                    .withAttributeDefinitions(new AttributeDefinition("id", ScalarAttributeType.S))
                    .withProvisionedThroughput(new ProvisionedThroughput(5L, 5L));

            Table table = dynamoDB.createTable(request);

            try {
                table.waitForActive();
                logger.info("PRs table created successfully.");
                return table;
            } catch (InterruptedException ie) {
                logger.error("Error while waiting for the table to become active.", ie);
                throw new RuntimeException(ie);
            }
        } catch (Exception e) {
            logger.error("Error setting up DynamoDB", e);
            throw new RuntimeException(e);
        }
    }

    // Add PR to the DynamoDB store
    public static void addPrToStore(Map<String, Object> prInfo) {
        Table table = createDynamoDBClient().getTable(TABLE_NAME);
        Item item = new Item().withPrimaryKey("id", prInfo.get("id"));
        prInfo.forEach(item::with); // Add all fields to the item

        table.putItem(item);
        logger.info("PR {} has been added or updated in the store.", prInfo.get("name"));
    }

    // Get all PRs from the DynamoDB store
    public static List<Item> getPrsFromStore() {
        Table table = createDynamoDBClient().getTable(TABLE_NAME);
        ScanSpec scanSpec = new ScanSpec();

        List<Item> items = table.scan(scanSpec).toList();
        logger.info("Retrieved {} PRs from the store.", items.size());
        return items;
    }

    // Get a PR by its ID
    public static Item getPrById(String prId) {
        Table table = createDynamoDBClient().getTable(TABLE_NAME);
        Item item = table.getItem("id", prId);

        if (item != null) {
            logger.info("Retrieved PR {} from the store.", prId);
        } else {
            logger.warn("PR {} not found in the store.", prId);
        }
        return item;
    }

    // Remove a PR by its ID
    public static void removePrById(String prId) {
        Table table = createDynamoDBClient().getTable(TABLE_NAME);
        table.deleteItem("id", prId);
        logger.info("PR {} has been removed from the store.", prId);
    }

    // Get PRs by user ID
    public static List<Item> getUserPrs(String userId) {
        Table table = createDynamoDBClient().getTable(TABLE_NAME);
        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#submitter_id", "submitter_id");

        ScanSpec scanSpec = new ScanSpec()
                .withFilterExpression("#submitter_id = :v_user_id")
                .withValueMap(new ValueMap().withString(":v_user_id", userId));

        List<Item> items = table.scan(scanSpec).toList();
        logger.info("Retrieved {} PRs for user {}.", items.size(), userId);
        return items;
    }

    // Get PRs by channel ID
    public static List<Item> getChannelPrs(String channelId) {
        Table table = createDynamoDBClient().getTable(TABLE_NAME);
        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#channel_id", "channel_id");

        ScanSpec scanSpec = new ScanSpec()
                .withFilterExpression("#channel_id = :v_channel_id")
                .withValueMap(new ValueMap().withString(":v_channel_id", channelId));

        List<Item> items = table.scan(scanSpec).toList();
        logger.info("Retrieved {} PRs from channel {}.", items.size(), channelId);
        return items;
    }

    // Delete all PRs from the table
    public static void deleteAllPrs() {
        Table table = createDynamoDBClient().getTable(TABLE_NAME);
        List<Item> items = getPrsFromStore();

        items.forEach(item -> table.deleteItem("id", item.getString("id")));
        logger.info("Deleted {} items from the PRs table.", items.size());
    }
}
