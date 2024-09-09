import com.slack.api.bolt.App;
import com.slack.api.bolt.socket_mode.SocketModeApp;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.util.SlackAppLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dotenv.Dotenv;
import database.Database;
import databaseChannelSettings.DatabaseChannelSettings;

public class AppMain {

    private static final Logger logger = LoggerFactory.getLogger(AppMain.class);

    public static void main(String[] args) throws Exception {
        Dotenv dotenv = Dotenv.load();

        AppConfig config = new AppConfig();
        config.setSingleTeamBotToken(dotenv.get("SLACK_BOT_TOKEN"));

        App app = new App(config);

        RegisterListeners.register(app);

        // Initialize DynamoDB setup methods (translated from Python)
        DatabaseChannelSettings.setupDynamodbSettings();
        Database.setupDynamodb();

        // Start the app with Socket Mode
        SocketModeApp socketModeApp = new SocketModeApp(dotenv.get("SLACK_APP_TOKEN"), app);
        socketModeApp.start();
    }
}
