package com.rescuereach.service.notification;

import android.content.Context;
import android.util.Log;

import com.onesignal.OSDeviceState;
import com.onesignal.OSNotification;
import com.onesignal.OSNotificationAction;
import com.onesignal.OSNotificationOpenedResult;
import com.onesignal.OSNotificationReceivedEvent;
import com.onesignal.OneSignal;
import com.onesignal.OneSignal.OSNotificationOpenedHandler;
import com.onesignal.OneSignal.OSNotificationWillShowInForegroundHandler;
import com.onesignal.OSInAppMessageAction;
import com.onesignal.OneSignal.OSInAppMessageClickHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for handling notifications via OneSignal
 * Provides methods for sending notifications, managing user tags,
 * and handling notification events
 */
public class NotificationService {
    private static final String TAG = "NotificationService";

    private static NotificationService instance;
    private final Context context;
    private NotificationActionListener actionListener;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    private static final String TAG_ROLE = "role";
    private static final String TAG_VOLUNTEER = "volunteer";
    private static final String TAG_REGION = "region";
    private static final String TAG_LAST_ACTIVE = "last_active";
    private static final String TAG_EMERGENCY_PREFERENCE = "emergency_preference";

    // External ID types
    private static final String EXTERNAL_ID_PHONE = "phone";

    /**
     * Private constructor to prevent direct instantiation
     */
    private NotificationService(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Get singleton instance of NotificationService
     * @param context Application context
     * @return NotificationService instance
     */
    public static synchronized NotificationService getInstance(Context context) {
        if (instance == null) {
            instance = new NotificationService(context);
        }
        return instance;
    }

    /**
     * Initialize notification handlers
     * Must be called after OneSignal is initialized in Application class
     */
    public void initialize() {
        if (isInitialized.getAndSet(true)) {
            Log.d(TAG, "NotificationService already initialized");
            return;
        }

        // Set notification opened handler
        OneSignal.setNotificationOpenedHandler(new OSNotificationOpenedHandler() {
            @Override
            public void notificationOpened(OSNotificationOpenedResult result) {
                handleNotificationOpened(result);
            }
        });

        // Set foreground notification handler
        OneSignal.setNotificationWillShowInForegroundHandler(new OSNotificationWillShowInForegroundHandler() {
            @Override
            public void notificationWillShowInForeground(OSNotificationReceivedEvent notificationReceivedEvent) {
                OSNotification notification = notificationReceivedEvent.getNotification();

                // Pass to our handler
                handleForegroundNotification(notification);

                // Allow the notification to display
                notificationReceivedEvent.complete(notification);
            }
        });

        // Set in-app message click handler
        OneSignal.setInAppMessageClickHandler(new OSInAppMessageClickHandler() {
            @Override
            public void inAppMessageClicked(OSInAppMessageAction action) {
                handleInAppMessageAction(action);
            }
        });

        Log.d(TAG, "NotificationService initialized");
    }

    /**
     * Set notification action listener for the app to respond to notifications
     * @param listener Listener implementation
     */
    public void setNotificationActionListener(NotificationActionListener listener) {
        this.actionListener = listener;
    }

    /**
     * Handle notification opened event
     * @param result The notification open result
     */
    private void handleNotificationOpened(OSNotificationOpenedResult result) {
        try {
            OSNotification notification = result.getNotification();
            JSONObject data = notification.getAdditionalData();
            String notificationType = data != null ? data.optString("type", "general") : "general";
            String notificationId = notification.getNotificationId();

            Log.d(TAG, "Notification opened: " + notificationType + " - ID: " + notificationId);

            if (actionListener != null) {
                actionListener.onNotificationOpened(notificationType, data);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling opened notification", e);
        }
    }

    /**
     * Handle foreground notification
     * @param notification The notification
     */
    private void handleForegroundNotification(OSNotification notification) {
        try {
            JSONObject data = notification.getAdditionalData();
            String notificationType = data != null ? data.optString("type", "general") : "general";
            String notificationId = notification.getNotificationId();

            Log.d(TAG, "Foreground notification: " + notificationType + " - ID: " + notificationId);

            if (actionListener != null) {
                actionListener.onForegroundNotification(notificationType, data);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling foreground notification", e);
        }
    }

    /**
     * Handle in-app message action
     * @param action The in-app message action
     */
    private void handleInAppMessageAction(OSInAppMessageAction action) {
        try {
            String actionId = action.getClickName();
            JSONObject data = action.getClickUrl() != null ?
                    new JSONObject().put("url", action.getClickUrl()) :
                    new JSONObject();

            Log.d(TAG, "In-app message action: " + actionId);

            if (actionListener != null) {
                actionListener.onInAppMessageAction(actionId, data);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling in-app message action", e);
        }
    }

    /**
     * Set user identifier to link device with user account
     * @param phoneNumber User's phone number
     */
    public void setUserIdentifier(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            Log.w(TAG, "Cannot set user identifier: Phone number is empty");
            return;
        }

        // Set OneSignal external user ID
        OneSignal.setExternalUserId(phoneNumber);
        Log.d(TAG, "User identifier set: " + phoneNumber);
    }

    /**
     * Set user as a volunteer
     * @param isVolunteer Whether user is a volunteer
     */
    public void setUserAsVolunteer(boolean isVolunteer) {
        sendTag(TAG_VOLUNTEER, isVolunteer ? "true" : "false");
        Log.d(TAG, "User set as volunteer: " + isVolunteer);
    }

    /**
     * Set user's role (citizen or responder)
     * @param role The role (should be "citizen" or "responder")
     */
    public void setUserRole(String role) {
        sendTag(TAG_ROLE, role);
        Log.d(TAG, "User role set: " + role);
    }

    /**
     * Set user's region for targeting notifications
     * @param state State/province name
     */
    public void setUserRegion(String state) {
        if (state != null && !state.isEmpty()) {
            sendTag(TAG_REGION, state);
            Log.d(TAG, "User region set: " + state);
        }
    }

    /**
     * Set user's emergency preference for targeting specific emergency types
     * @param preference Emergency type preference (police, fire, medical)
     */
    public void setEmergencyPreference(String preference) {
        if (preference != null && !preference.isEmpty()) {
            sendTag(TAG_EMERGENCY_PREFERENCE, preference.toLowerCase());
            Log.d(TAG, "Emergency preference set: " + preference);
        }
    }

    /**
     * Update user's last active timestamp
     */
    public void updateLastActive() {
        sendTag(TAG_LAST_ACTIVE, String.valueOf(System.currentTimeMillis() / 1000));
    }

    /**
     * Send a tag to OneSignal for user segmentation
     * @param key Tag key
     * @param value Tag value
     */
    public void sendTag(String key, String value) {
        if (key != null && !key.isEmpty() && value != null) {
            OneSignal.sendTag(key, value);
        }
    }

    /**
     * Send multiple tags to OneSignal at once
     * @param tags Map of tag keys and values
     */
    public void sendTags(Map<String, String> tags) {
        if (tags != null && !tags.isEmpty()) {
            try {
                JSONObject jsonTags = new JSONObject();
                for (Map.Entry<String, String> entry : tags.entrySet()) {
                    jsonTags.put(entry.getKey(), entry.getValue());
                }
                OneSignal.sendTags(jsonTags);
            } catch (JSONException e) {
                Log.e(TAG, "Error sending tags", e);
            }
        }
    }

    /**
     * Delete a tag from the user
     * @param key Tag key to delete
     */
    public void deleteTag(String key) {
        if (key != null && !key.isEmpty()) {
            OneSignal.deleteTag(key);
        }
    }

    /**
     * Delete multiple tags from the user
     * @param keys List of tag keys to delete
     */
    public void deleteTags(List<String> keys) {
        if (keys != null && !keys.isEmpty()) {
            try {
                // Convert the list to a JSONArray
                JSONArray jsonArray = new JSONArray();
                for (String key : keys) {
                    jsonArray.put(key);
                }
                OneSignal.deleteTags(jsonArray.toString());
            } catch (Exception e) {
                Log.e(TAG, "Error deleting tags", e);
            }
        }
    }

    /**
     * Send a notification to specific users (requires REST API key)
     * This is a server-side operation that would typically be done through a backend service
     * @param heading Notification heading
     * @param message Notification message
     * @param additionalData Additional data to send with the notification
     * @param targetSegment Target segment (e.g., "All", "Active Users", etc.)
     * @return Whether the request was sent successfully
     */
    public boolean sendNotification(String heading, String message, JSONObject additionalData, String targetSegment) {
        // NOTE: OneSignal notifications to other users should be sent from your backend
        // This is just a placeholder for this functionality
        Log.d(TAG, "Server notification requested: " + heading + " - Target: " + targetSegment);

        // In a real implementation, this would make a REST API call to OneSignal
        return false;
    }

    /**
     * Get the OneSignal device state
     * @return OSDeviceState or null if not available
     */
    public OSDeviceState getDeviceState() {
        return OneSignal.getDeviceState();
    }

    /**
     * Get the OneSignal user ID (player ID)
     * @return OneSignal user ID or null if not available
     */
    public String getOneSignalUserId() {
        OSDeviceState deviceState = OneSignal.getDeviceState();
        return deviceState != null ? deviceState.getUserId() : null;
    }

    /**
     * Check if push notifications are enabled
     * @return true if enabled, false otherwise
     */
    public boolean arePushNotificationsEnabled() {
        OSDeviceState deviceState = OneSignal.getDeviceState();
        return deviceState != null && deviceState.areNotificationsEnabled();
    }

    /**
     * Prompt the user for notification permissions
     * This is required for iOS but optional on Android
     */
    public void promptForPushNotifications() {
        OneSignal.promptForPushNotifications();
    }

    /**
     * Set subscription status for push notifications
     * @param isSubscribed Whether the user is subscribed to notifications
     */
    public void setPushNotificationSubscription(boolean isSubscribed) {
        OneSignal.disablePush(!isSubscribed);
    }

    /**
     * Clear all notification data for this user
     * Useful when user logs out
     */
    public void clearNotificationData() {
        // Remove external user ID
        OneSignal.removeExternalUserId();

        // Delete all tags - OneSignal 4.8.6 doesn't support deleteTag with String array,
        // so we need to delete them one by one
        deleteTag(TAG_ROLE);
        deleteTag(TAG_VOLUNTEER);
        deleteTag(TAG_REGION);
        deleteTag(TAG_LAST_ACTIVE);
        deleteTag(TAG_EMERGENCY_PREFERENCE);

        Log.d(TAG, "Notification data cleared");
    }

    /**
     * Interface for notification action listeners
     */
    public interface NotificationActionListener {
        void onNotificationOpened(String type, JSONObject data);
        void onForegroundNotification(String type, JSONObject data);
        void onInAppMessageAction(String actionId, JSONObject data);
    }
}