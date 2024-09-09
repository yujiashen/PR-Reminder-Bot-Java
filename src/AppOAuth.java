import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.oauth.OAuthCallbackOptions;
import com.slack.api.bolt.oauth.OAuthSuccessHandler;
import com.slack.api.bolt.oauth.OAuthFailureHandler;
import com.slack.api.bolt.oauth.OAuthSettings;
import com.slack.api.bolt.socket_mode.SocketModeApp;
import com.slack.api.model.event.AppMentionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dotenv.Dotenv;

import listeners.RegisterListeners;
import com.slack.api.bolt.response.Response;
import com.slack.api.bolt.util.SlackAppLogger;
import com.slack.api.oauth.state.OAuthStateService;
import com.slack.api.oauth.state.impl.FileOAuthStateService;
import com.slack.api.oauth.service.OAuthV2StateService;
import com.slack.api.oauth.service.impl.FileOAuthStateService;
import com.slack.api.oauth.service.impl.FileInstallationService;

public class AppOAuth {

    private static final Logger logger = LoggerFactory.getLogger(AppOAuth.class);

    public static void main(String[] args) throws Exception {
        // Load environment variables
        Dotenv dotenv = Dotenv.load();

        // App configuration
        AppConfig config = new AppConfig();
        config.setSigningSecret(dotenv.get("SLACK_SIGNING_SECRET"));
        config.setClientId(dotenv.get("SLACK_CLIENT_ID"));
        config.setClientSecret(dotenv.get("SLACK_CLIENT_SECRET"));
        config.setScope("channels:history,chat:write,commands");

        // Create state store for OAuth flow
        OAuthStateService stateService = new FileOAuthStateService("state-store", 600); // 600 seconds expiration

        // Create installation store for OAuth flow
        OAuthV2StateService installationService = new FileInstallationService("installation-store");

        // Success handler for OAuth
        OAuthSuccessHandler successHandler = (req, resp, context) -> {
            logger.info("Installation succeeded!");
            return context.ack(); // Acknowledge the successful installation
        };

        // Failure handler for OAuth
        OAuthFailureHandler failureHandler = (req, resp, context) -> {
            logger.error("Installation failed: {}", context.getError());
            return context.ack(); // Acknowledge the failed installation
        };

        // Set up OAuth settings
        OAuthCallbackOptions callbackOptions = OAuthCallbackOptions.builder()
                .success(successHandler)
                .failure(failureHandler)
                .build();

        OAuthSettings oauthSettings = OAuthSettings.builder()
                .clientId(dotenv.get("SLACK_CLIENT_ID"))
                .clientSecret(dotenv.get("SLACK_CLIENT_SECRET"))
                .scope("channels:history,chat:write,commands")
                .stateService(stateService)
                .installationService(installationService)
                .callbackOptions(callbackOptions)
                .installPath("/slack/install")
                .redirectUriPath("/slack/oauth_redirect")
                .build();

        // Create the Slack App
        App app = new App(config).asOAuthApp(oauthSettings);

        // Register listeners
        RegisterListeners.register(app);

        // Start the app
        app.start(3000);
    }
}
