package com.rescuereach.service.notification;

import android.content.Context;
import android.util.Log;

import com.onesignal.OneSignal;
import com.onesignal.debug.LogLevel;
import com.onesignal.notifications.INotificationReceivedEvent;
import com.onesignal.notifications.IDisplayableMutableNotification;
import com.onesignal.notifications.INotificationClickEvent;
import com.onesignal.notifications.INotification;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Manager class for OneSignal integration
 * Handles push notifications and user identification
 */
public class OneSignalManager {
    private static final String TAG = "OneSignalManager";

    private final Context context;
    private String oneSignalUserId;
    private NotificationPreferenceRepository preferenceRepository;

    /**
     * Constructor initializes the OneSignal manager with context
     * @param context Application context
     */
    public OneSignalManager(Context context) {
        this.context = context.getApplicationContext();
        this.preferenceRepository = new NotificationPreferenceRepository(context);

        // Set up notification handlers
        setupNotificationHandlers();

        // Get the OneSignal user ID
        refreshOneSignalUserId();
    }

    /**
     * Refresh and get the latest OneSignal user ID
     */
    private void refreshOneSignalUserId() {
        try {
            // Get the push subscription ID for this device
            String userId = OneSignal.getUser().getPushSubscription().getId();
            if (userId != null) {
                oneSignalUserId = userId;
                Log.d(TAG, "OneSignal User ID: " + oneSignalUserId);
            } else {
                Log.w(TAG, "OneSignal User ID is null");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting OneSignal User ID", e);
        }
    }

    /**
     * Get the OneSignal user ID for targeting this device
     * @return The OneSignal user ID
     */
    public String getOneSignalUserId() {
        if (oneSignalUserId == null) {
            refreshOneSignalUserId();
        }
        return oneSignalUserId;
    }

    /**
     * Set up notification handlers for OneSignal events
     */
    private void setupNotificationHandlers() {
        try {
            // Handle notifications that will be displayed
            OneSignal.getNotifications().setNotificationWillShowInForegroundHandler(this::handleNotificationReceived);

            // Handle notification opened events
            OneSignal.getNotifications().setNotificationClickHandler(this::handleNotificationOpened);

            Log.d(TAG, "OneSignal notification handlers set up successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error setting up OneSignal notification handlers", e);
        }
    }

    /**
     * Handle notification received while app is in foreground
     * @param event The notification received event
     */
    private void handleNotificationReceived(INotificationReceivedEvent event) {
        try {
            IDisplayableMutableNotification notification = event.getNotification();

            // Get notification data
            String title = notification.getTitle();
            String body = notification.getBody();
            JSONObject data = notification.getAdditionalData();

            Log.d(TAG, "Notification Received - Title: " + title + ", Body: " + body +
                    ", Data: " + (data != null ? data.toString() : "null"));

            // Check if this notification is for an emergency report
            if (data != null && data.has("reportId")) {
                try {
                    String reportId = data.getString("reportId");
                    Log.d(TAG, "Emergency notification for report: " + reportId);

                    // Apply higher priority for emergency notifications
                    // This ensures they get attention even in foreground
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing notification data", e);
                }
            }

            // Process notification based on type and user preferences
            boolean shouldShow = shouldShowNotification(notification);

            // Complete processing with decision whether to show
            if (shouldShow) {
                event.preventDefault(false); // Allow display
            } else {
                event.preventDefault(true); // Prevent display
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in handleNotificationReceived", e);
            // Don't prevent display in case of errors
            event.preventDefault(false);
        }
    }

    /**
     * Determine if notification should be shown based on preferences
     * @param notification The notification to evaluate
     * @return True if notification should be shown
     */
    private boolean shouldShowNotification(IDisplayableMutableNotification notification) {
        // Extract any category from the notification data
        String category = "general";
        try {
            JSONObject data = notification.getAdditionalData();
            if (data != null && data.has("category")) {
                category = data.getString("category");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting notification category", e);
        }

        // Always show emergency notifications
        if ("emergency".equals(category)) {
            return true;
        }

        // Check category preference
        return preferenceRepository.isChannelEnabled(category);
    }

    /**
     * Handle notification opened event
     * @param event The notification opened result
     */
    private void handleNotificationOpened(INotificationClickEvent event) {
        try {
            INotification notification = event.getNotification();

            // Get notification data
            String title = notification.getTitle();
            String body = notification.getBody();
            JSONObject data = notification.getAdditionalData();

            Log.d(TAG, "Notification Opened - Title: " + title + ", Body: " + body +
                    ", Data: " + (data != null ? data.toString() : "null"));

            if (data != null) {
                try {
                    // Check if this is an emergency notification
                    if (data.has("reportId")) {
                        String reportId = data.getString("reportId");
                        String emergencyType = data.optString("emergencyType");
                        String status = data.optString("status");

                        // Handle navigation in NotificationNavigator class
                        NotificationNavigator.navigateToEmergencyDetails(context, reportId, emergencyType, status);
                    }

                    // Check for specific action
                    if (data.has("clickAction")) {
                        String action = data.getString("clickAction");
                        handleClickAction(action, data);
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing notification data", e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in handleNotificationOpened", e);
        }
    }

    /**
     * Handle specific click actions from notifications
     */
    private void handleClickAction(String action, JSONObject data) throws JSONException {
        switch (action) {
            case "OPEN_SOS_DETAILS":
                if (data.has("reportId")) {
                    NotificationNavigator.navigateToEmergencyDetails(
                            context,
                            data.getString("reportId"),
                            data.optString("emergencyType"),
                            data.optString("status"));
                }
                break;

            case "VIEW_MAP":
                if (data.has("latitude") && data.has("longitude")) {
                    double lat = data.getDouble("latitude");
                    double lng = data.getDouble("longitude");
                    NotificationNavigator.navigateToMap(context, lat, lng);
                }
                break;

            case "OPEN_SETTINGS":
                NotificationNavigator.navigateToSettings(context);
                break;

            default:
                Log.d(TAG, "Unknown click action: " + action);
                break;
        }
    }

    /**
     * Set the user ID for OneSignal (for targeting notifications)
     * @param userId User ID to set
     */
    public void setUserId(String userId) {
        if (userId != null && !userId.isEmpty()) {
            Log.d(TAG, "Setting OneSignal external user ID: " + userId);

            // Set as external user ID in OneSignal (updated for 5.x)
            OneSignal.login(userId);

            // Also set tags for user info (allows more segmentation options)
            try {
                Map<String, String> tags = new HashMap<>();
                tags.put("user_id", userId);
                OneSignal.User.addTags(tags);
            } catch (Exception e) {
                Log.e(TAG, "Error setting user tags", e);
            }
        }
    }

    /**
     * Clear user ID when logging out
     */
    public void clearUserId() {
        OneSignal.logout();
        OneSignal.User.removeTags(new String[]{"user_id"});
    }

    /**
     * Set additional user information as tags
     * @param userInfo Map of user information
     */
    public void setUserInfo(Map<String, String> userInfo) {
        if (userInfo == null || userInfo.isEmpty()) {
            return;
        }

        try {
            OneSignal.User.addTags(userInfo);
        } catch (Exception e) {
            Log.e(TAG, "Error setting user info tags", e);
        }
    }

    /**
     * Send a push notification to a specific user
     * Note: In production, this should be done from your server
     */
    public void sendPushNotification(String targetUserId, String title, String message, Map<String, String> data) {
        try {
            Log.d(TAG, "Sending push notification to: " + targetUserId);

            // Create notification content JSON
            JSONObject notificationContent = new JSONObject();

            // Add title and message
            JSONObject headings = new JSONObject();
            headings.put("en", title);

            JSONObject contents = new JSONObject();
            contents.put("en", message);

            notificationContent.put("headings", headings);
            notificationContent.put("contents", contents);

            // Set Android-specific settings
            JSONObject androidSettings = new JSONObject();
            androidSettings.put("android_channel_id", "emergency_alerts");
            androidSettings.put("android_accent_color", "FF9D0000"); // Emergency red color
            androidSettings.put("android_group", "rescuereach_alerts");
            notificationContent.put("android_channel_id", "emergency_alerts");

            // Add custom data if provided
            if (data != null && !data.isEmpty()) {
                JSONObject dataObj = new JSONObject();
                for (Map.Entry<String, String> entry : data.entrySet()) {
                    dataObj.put(entry.getKey(), entry.getValue());
                }
                notificationContent.put("data", dataObj);
            }

            // Target specific user by external user ID
            JSONArray includedExternalUserIds = new JSONArray();
            includedExternalUserIds.put(targetUserId);
            notificationContent.put("include_external_user_ids", includedExternalUserIds);

            // Send the notification - Note: This must be done through server API in production
            OneSignal.postNotification(notificationContent, null);

        } catch (JSONException e) {
            Log.e(TAG, "Error creating notification JSON", e);
        }
    }

    /**
     * Subscribe to specific topics for targeted notifications
     * @param topic Topic to subscribe to
     */
    public void subscribeToTopic(String topic) {
        try {
            Map<String, String> tags = new HashMap<>();
            tags.put(topic, "true");
            OneSignal.User.addTags(tags);

            // Save preference locally
            preferenceRepository.setChannelEnabled(topic, true);

            Log.d(TAG, "Subscribed to topic: " + topic);
        } catch (Exception e) {
            Log.e(TAG, "Error subscribing to topic", e);
        }
    }

    /**
     * Unsubscribe from a topic
     * @param topic Topic to unsubscribe from
     */
    public void unsubscribeFromTopic(String topic) {
        OneSignal.User.removeTag(topic);

        // Save preference locally
        preferenceRepository.setChannelEnabled(topic, false);

        Log.d(TAG, "Unsubscribed from topic: " + topic);
    }

    /**
     * Check if a topic is subscribed
     * @param topic Topic to check
     */
    public boolean isSubscribedToTopic(String topic) {
        return preferenceRepository.isChannelEnabled(topic);
    }

    /**
     * Enable or disable notification sounds
     */
    public void setNotificationSoundEnabled(boolean enabled) {
        preferenceRepository.setNotificationSoundEnabled(enabled);
    }

    /**
     * Check if notification sounds are enabled
     */
    public boolean isNotificationSoundEnabled() {
        return preferenceRepository.isNotificationSoundEnabled();
    }

    /**
     * Inner class for notification preferences storage
     */
    private static class NotificationPreferenceRepository {
        private static final String PREF_NAME = "notification_preferences";
        private static final String KEY_PREFIX_CHANNEL = "channel_";
        private static final String KEY_SOUND = "notification_sound_enabled";

        private final Context context;

        public NotificationPreferenceRepository(Context context) {
            this.context = context.getApplicationContext();
        }

        public boolean isChannelEnabled(String channel) {
            return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .getBoolean(KEY_PREFIX_CHANNEL + channel, true); // Default to true
        }

        public void setChannelEnabled(String channel, boolean enabled) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_PREFIX_CHANNEL + channel, enabled)
                    .apply();
        }

        public boolean isNotificationSoundEnabled() {
            return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .getBoolean(KEY_SOUND, true); // Default to true
        }

        public void setNotificationSoundEnabled(boolean enabled) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_SOUND, enabled)
                    .apply();
        }
    }

    /**
     * Inner utility class for navigation from notifications
     * This follows the Single Responsibility Principle by separating navigation logic
     */
    private static class NotificationNavigator {
        public static void navigateToEmergencyDetails(Context context, String reportId,
                                                      String emergencyType, String status) {
            // This would be implemented to navigate to the appropriate activity/fragment
            Log.d(TAG, "Navigate to emergency details: " + reportId);
            // In a real implementation, you would start an activity here
        }

        public static void navigateToMap(Context context, double latitude, double longitude) {
            // Navigate to map with the given coordinates
            Log.d(TAG, "Navigate to map: " + latitude + ", " + longitude);
        }

        public static void navigateToSettings(Context context) {
            // Navigate to settings screen
            Log.d(TAG, "Navigate to settings");
        }
    }
}