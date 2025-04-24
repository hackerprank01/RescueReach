package com.rescuereach.service.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

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
import com.rescuereach.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
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
    private final Handler mainHandler;
    private final Executor backgroundExecutor;

    private static final String TAG_ROLE = "role";
    private static final String TAG_VOLUNTEER = "volunteer";
    private static final String TAG_REGION = "region";
    private static final String TAG_LAST_ACTIVE = "last_active";
    private static final String TAG_EMERGENCY_PREFERENCE = "emergency_preference";

    // External ID types
    private static final String EXTERNAL_ID_PHONE = "phone";

    // Test notification channel
    private static final String TEST_CHANNEL_ID = "test_notification_channel";
    private static final int TEST_NOTIFICATION_ID = 2001;

    /**
     * Private constructor to prevent direct instantiation
     */
    private NotificationService(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.backgroundExecutor = Executors.newSingleThreadExecutor();
    }

    /**
     * Get singleton instance of NotificationService
     * @param context Application context
     * @return NotificationService instance
     */
    public static synchronized NotificationService getInstance(Context context) {
        if (instance == null && context != null) {
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

        try {
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

            // Create notification channels for testing
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createTestNotificationChannel();
            }

            Log.d(TAG, "NotificationService initialized");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing notification handlers", e);
        }
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
                mainHandler.post(() -> {
                    try {
                        actionListener.onNotificationOpened(notificationType, data);
                    } catch (Exception e) {
                        Log.e(TAG, "Error in notification opened callback", e);
                    }
                });
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
                mainHandler.post(() -> {
                    try {
                        actionListener.onForegroundNotification(notificationType, data);
                    } catch (Exception e) {
                        Log.e(TAG, "Error in foreground notification callback", e);
                    }
                });
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
                mainHandler.post(() -> {
                    try {
                        actionListener.onInAppMessageAction(actionId, data);
                    } catch (Exception e) {
                        Log.e(TAG, "Error in in-app message action callback", e);
                    }
                });
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

        backgroundExecutor.execute(() -> {
            try {
                // Set OneSignal external user ID
                OneSignal.setExternalUserId(phoneNumber);
                Log.d(TAG, "User identifier set: " + phoneNumber);
            } catch (Exception e) {
                Log.e(TAG, "Error setting user identifier", e);
            }
        });
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
            backgroundExecutor.execute(() -> {
                try {
                    OneSignal.sendTag(key, value);
                } catch (Exception e) {
                    Log.e(TAG, "Error sending tag: " + key, e);
                }
            });
        }
    }

    /**
     * Send multiple tags to OneSignal at once
     * @param tags Map of tag keys and values
     */
    public void sendTags(Map<String, String> tags) {
        if (tags != null && !tags.isEmpty()) {
            backgroundExecutor.execute(() -> {
                try {
                    JSONObject jsonTags = new JSONObject();
                    for (Map.Entry<String, String> entry : tags.entrySet()) {
                        jsonTags.put(entry.getKey(), entry.getValue());
                    }
                    OneSignal.sendTags(jsonTags);
                } catch (JSONException e) {
                    Log.e(TAG, "Error sending tags", e);
                }
            });
        }
    }

    /**
     * Delete a tag from the user
     * @param key Tag key to delete
     */
    public void deleteTag(String key) {
        if (key != null && !key.isEmpty()) {
            backgroundExecutor.execute(() -> {
                try {
                    OneSignal.deleteTag(key);
                } catch (Exception e) {
                    Log.e(TAG, "Error deleting tag: " + key, e);
                }
            });
        }
    }

    /**
     * Delete multiple tags from the user
     * @param keys List of tag keys to delete
     */
    public void deleteTags(List<String> keys) {
        if (keys != null && !keys.isEmpty()) {
            backgroundExecutor.execute(() -> {
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
            });
        }
    }

    /**
     * Send an emergency notification to specific regions
     * @param emergencyType Type of emergency (POLICE, FIRE, MEDICAL)
     * @param data Additional data to include with notification
     * @param targetRegion Target region/state for the notification
     */
    public void sendEmergencyNotification(String emergencyType, Map<String, Object> data, String targetRegion) {
        Log.d(TAG, "Sending emergency notification for: " + emergencyType + " to region: " + targetRegion);

        backgroundExecutor.execute(() -> {
            try {
                // In a real implementation, this would use OneSignal's REST API
                // to send notifications to users in the target region

                // For now, we'll just log that it would happen
                Log.d(TAG, "Would send emergency notification to: " + targetRegion);

                // Send a test notification to the current device instead
                String heading = emergencyType + " EMERGENCY";
                String message = "Emergency reported" +
                        (targetRegion != null && !targetRegion.isEmpty() ? " in " + targetRegion : "");

                // Use our test method to show a local notification
                sendTestNotification(heading, message);

            } catch (Exception e) {
                Log.e(TAG, "Error sending emergency notification", e);
            }
        });
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

        // Send a test notification to the current device
        sendTestNotification(heading, message);

        // In a real implementation, this would make a REST API call to OneSignal
        return true;
    }

    /**
     * Send a direct test notification to the current device
     * For development and testing purposes
     * @param title Notification title
     * @param message Notification message
     */
    public void sendTestNotification(String title, String message) {
        backgroundExecutor.execute(() -> {
            try {
                // Get this device's OneSignal ID
                String deviceId = getOneSignalUserId();
                Log.d(TAG, "Sending test notification to device: " +
                        (deviceId != null ? deviceId : "unknown"));

                // First try OneSignal notification if we have a valid device ID
                if (deviceId != null && !deviceId.isEmpty()) {
                    // In a real app, this would use OneSignal's REST API to send
                    // For now, we'll create a local notification instead
                }

                // Create and show a local notification on the main thread
                mainHandler.post(() -> {
                    try {
                        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, TEST_CHANNEL_ID)
                                .setSmallIcon(R.drawable.ic_notification)
                                .setContentTitle(title)
                                .setContentText(message)
                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                                .setAutoCancel(true);

                        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

                        // Check for notification permission on Android 13+
                        if (Build.VERSION.SDK_INT < 33 ||
                                ActivityCompat.checkSelfPermission(context,
                                        android.Manifest.permission.POST_NOTIFICATIONS)
                                        == PackageManager.PERMISSION_GRANTED) {

                            // Each test notification gets a unique ID based on time
                            int id = (int) ((System.currentTimeMillis() / 1000) % Integer.MAX_VALUE);
                            notificationManager.notify(id, builder.build());

                            Log.d(TAG, "Test notification displayed with ID: " + id);
                        } else {
                            Log.w(TAG, "Cannot show notification: Permission not granted");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error showing test notification", e);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to send test notification", e);
            }
        });
    }

    /**
     * Create notification channel for test notifications (required for Android O+)
     */
    private void createTestNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                NotificationManager notificationManager =
                        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

                if (notificationManager != null) {
                    NotificationChannel channel = new NotificationChannel(
                            TEST_CHANNEL_ID,
                            "Test Notifications",
                            NotificationManager.IMPORTANCE_HIGH);
                    channel.setDescription("Channel for test notifications");
                    channel.enableLights(true);
                    channel.enableVibration(true);
                    notificationManager.createNotificationChannel(channel);

                    Log.d(TAG, "Test notification channel created");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error creating test notification channel", e);
            }
        }
    }

    /**
     * Get the OneSignal device state
     * @return OSDeviceState or null if not available
     */
    public OSDeviceState getDeviceState() {
        try {
            return OneSignal.getDeviceState();
        } catch (Exception e) {
            Log.e(TAG, "Error getting device state", e);
            return null;
        }
    }

    /**
     * Get the OneSignal user ID (player ID)
     * @return OneSignal user ID or null if not available
     */
    public String getOneSignalUserId() {
        try {
            OSDeviceState deviceState = OneSignal.getDeviceState();
            return deviceState != null ? deviceState.getUserId() : null;
        } catch (Exception e) {
            Log.e(TAG, "Error getting OneSignal user ID", e);
            return null;
        }
    }

    /**
     * Check if push notifications are enabled
     * @return true if enabled, false otherwise
     */
    public boolean arePushNotificationsEnabled() {
        try {
            OSDeviceState deviceState = OneSignal.getDeviceState();
            return deviceState != null && deviceState.areNotificationsEnabled();
        } catch (Exception e) {
            Log.e(TAG, "Error checking if push notifications are enabled", e);
            return false;
        }
    }

    /**
     * Prompt the user for notification permissions
     * This is required for iOS but optional on Android
     */
    public void promptForPushNotifications() {
        try {
            OneSignal.promptForPushNotifications();
        } catch (Exception e) {
            Log.e(TAG, "Error prompting for push notifications", e);
        }
    }

    /**
     * Set subscription status for push notifications
     * @param isSubscribed Whether the user is subscribed to notifications
     */
    public void setPushNotificationSubscription(boolean isSubscribed) {
        try {
            OneSignal.disablePush(!isSubscribed);
        } catch (Exception e) {
            Log.e(TAG, "Error setting push notification subscription", e);
        }
    }

    /**
     * Clear all notification data for this user
     * Useful when user logs out
     */
    public void clearNotificationData() {
        backgroundExecutor.execute(() -> {
            try {
                // Remove external user ID
                OneSignal.removeExternalUserId();

                // Delete all tags
                deleteTag(TAG_ROLE);
                deleteTag(TAG_VOLUNTEER);
                deleteTag(TAG_REGION);
                deleteTag(TAG_LAST_ACTIVE);
                deleteTag(TAG_EMERGENCY_PREFERENCE);

                Log.d(TAG, "Notification data cleared");
            } catch (Exception e) {
                Log.e(TAG, "Error clearing notification data", e);
            }
        });
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