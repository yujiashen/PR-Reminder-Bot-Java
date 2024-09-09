public class Utils {

    public static String getStatus(Map<String, Object> pr) {
        int reviewsNeeded = (int) pr.get("reviews_needed") - (int) pr.get("reviews_received");

        if (((List<?>) pr.get("attention_requests")).size() > 0) {
            return "attention needed";
        } else if (reviewsNeeded > 0) {
            return "needs " + reviewsNeeded + " reviews";
        } else if (reviewsNeeded == 0) {
            return "PR reviewed!";
        }
        return null;
    }

    public static String getUsername(Slack client, String userId) {
        try {
            Map<String, Object> userInfo = client.usersInfo(req -> req.user(userId)).getUser();
            return (String) userInfo.get("real_name");
        } catch (Exception e) {
            System.err.println("Error fetching user name for ID " + userId + ": " + e.getMessage());
            return "Unknown User";
        }
    }

    public static boolean isValidInt(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
