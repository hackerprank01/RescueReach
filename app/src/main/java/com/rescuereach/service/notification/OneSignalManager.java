package com.rescuereach.service.notification;

import android.content.Context;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.onesignal.OSDeviceState;
import com.onesignal.OneSignal;
import com.onesignal.OSNotification;
import com.onesignal.OSNotificationOpenedResult;
import com.onesignal.OSNotificationReceivedEvent;
import com.rescuereach.data.model.EmergencyContact;
import com.rescuereach.data.model.EmergencyService;
import com.rescuereach.data.model.SOSReport;
import com.rescuereach.service.auth.UserSessionManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manager class for handling OneSignal push notifications and SMS integration
 */
public class OneSignalManager {
    private static final String TAG = "OneSignalManager";

    private final Context context;
    private final UserSessionManager sessionManager;
    private String oneSignalUserId;

    /**
     * Constructor
     */
    public OneSignalManager(Context context) {
        this.context = context.getApplicationContext();
        this.sessionManager = UserSessionManager.getInstance(context);

        // Set up notification handlers
        setupNotificationHandlers();

        // Get the OneSignal user ID
        OSDeviceState deviceState = OneSignal.getDeviceState();
        if (deviceState != null) {
            oneSignalUserId = deviceState.getUserId();
            Log.d(TAG, "OneSignal User ID: " + oneSignalUserId);
        }
    }

    /**
     * Set up notification handlers
     */
    private void setupNotificationHandlers() {
        // Set notification will show in foreground handler
        OneSignal.setNotificationWillShowInForegroundHandler(this::handleNotificationReceived);

        // Set notification opened handler
        OneSignal.setNotificationOpenedHandler(this::handleNotificationOpened);
    }

    /**
     * Handle notification received in foreground
     */
    private void handleNotificationReceived(OSNotificationReceivedEvent notificationReceivedEvent) {
        OSNotification notification = notificationReceivedEvent.getNotification();

        // Get notification data
        String title = notification.getTitle();
        String body = notification.getBody();
        Map<String, String> data = notification.getAdditionalData() != null ?
                notification.getAdditionalData().toString() : null;

        Log.d(TAG, "Notification Received - Title: " + title + ", Body: " + body +
                ", Data: " + data);

        // Complete processing (required)
        notificationReceivedEvent.complete(notification);
    }

    /**
     * Handle notification opened
     */
    private void handleNotificationOpened(OSNotificationOpenedResult result) {
        OSNotification notification = result.getNotification();

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

                    // TODO: Handle opening the appropriate activity to show the emergency details
                    Log.d(TAG, "Emergency notification for report: " + reportId +
                            ", Type: " + emergencyType + ", Status: " + status);

                    // You would typically start an activity here, or use a navigation component
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing notification data", e);
            }
        }
    }

    /**
     * Set the user ID for OneSignal (for targeting notifications)
     */
    public void setUserId(String userId) {
        if (userId != null && !userId.isEmpty()) {
            Log.d(TAG, "Setting OneSignal external user ID: " + userId);

            // Set as external user ID in OneSignal
            OneSignal.setExternalUserId(userId);

            // Also set tags for user info (allows more segmentation options)
            try {
                JSONObject tags = new JSONObject();
                tags.put("user_id", userId);
                tags.put("phone_number", sessionManager.getSavedPhoneNumber());
                tags.put("full_name", sessionManager.getFullName());

                OneSignal.sendTags(tags);
            } catch (JSONException e) {
                Log.e(TAG, "Error setting user tags", e);
            }
        }
    }

    /**
     * Clear user ID when logging out
     */
    public void clearUserId() {
        OneSignal.removeExternalUserId();
        OneSignal.deleteTags(new String[]{"user_id", "phone_number", "full_name"});
    }

    /**
     * Get the OneSignal user ID (for targeting this device)
     */
    public String getOneSignalUserId() {
        if (oneSignalUserId == null) {
            OSDeviceState deviceState = OneSignal.getDeviceState();
            if (deviceState != null) {
                oneSignalUserId = deviceState.getUserId();
            }
        }
        return oneSignalUserId;
    }

    /**
     * Send a push notification to a specific user
     * Note: In production, this should be done from your server
     */
    public void sendPushNotification(String targetUserId, String title, String message, Map<String, String> data) {
        try {
            Log.d(TAG, "Sending push notification to: " + targetUserId);

            // For security reasons, sending notifications should typically be done from your server
            // This is just for demonstration purposes
            Log.w(TAG, "Push notification sending should ideally be done from your server");

            // Create notification content JSON
            JSONObject notificationContent = new JSONObject();

            // Add title and message
            JSONObject headings = new JSONObject();
            headings.put("en", title);

            JSONObject contents = new JSONObject();
            contents.put("en", message);

            notificationContent.put("headings", headings);
            notificationContent.put("contents", contents);

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

            // Send the notification
            OneSignal.postNotification(notificationContent, null);

        } catch (JSONException e) {
            Log.e(TAG, "Error creating notification JSON", e);
        }
    }

    /**
     * Send emergency SMS to contacts using direct Android SMS API
     * (Since OneSignal SMS requires server-side implementation)
     */
    public void sendEmergencySMS(SOSReport report, List<EmergencyContact> contacts) {
        String message = createEmergencyMessage(report);

        // Use Android's SMS Manager as a direct approach
        SmsManager smsManager = SmsManager.getDefault();

        for (EmergencyContact contact : contacts) {
            if (contact.getPhoneNumber() != null && !contact.getPhoneNumber().isEmpty()) {
                try {
                    // For longer messages, we need to divide them
                    ArrayList<String> parts = smsManager.divideMessage(message);

                    // Send the SMS
                    smsManager.sendMultipartTextMessage(
                            contact.getFormattedPhoneNumber(),
                            null,
                            parts,
                            null,
                            null
                    );

                    Log.d(TAG, "Emergency SMS sent to: " + contact.getName() +
                            " (" + contact.getFormattedPhoneNumber() + ")");

                } catch (Exception e) {
                    Log.e(TAG, "Error sending SMS to " + contact.getName(), e);
                }
            }
        }
    }

    /**
     * Create emergency message for SMS
     */
    private String createEmergencyMessage(SOSReport report) {
        StringBuilder message = new StringBuilder();
        message.append("EMERGENCY ALERT: ");
        message.append(report.getUserFullName()).append(" has reported a ");
        message.append(report.getEmergencyType()).append(" emergency.\n\n");

        // Add location details
        if (report.getAddress() != null) {
            message.append("Location: ").append(report.getAddress());
            if (report.getCity() != null) message.append(", ").append(report.getCity());
            if (report.getState() != null) message.append(", ").append(report.getState());
            message.append("\n\n");
        } else {
            // Include coordinates if no address
            message.append(String.format("Location coordinates: %.6f, %.6f\n\n",
                    report.getLatitude(), report.getLongitude()));
        }

        // Add emergency service info if available
        EmergencyService service = report.getPrimaryEmergencyService();
        if (service != null) {
            message.append("Nearest ").append(report.getEmergencyType()).append(": ");
            message.append(service.getName());
            if (service.getDistance() > 0) {
                message.append(" (").append(service.getFormattedDistance()).append(")");
            }
            message.append("\n");

            if (service.getEmergencyNumber() != null && !service.getEmergencyNumber().isEmpty()) {
                message.append("Emergency service #: ").append(service.getEmergencyNumber());
                message.append("\n\n");
            }
        }

        // Add SOS ID
        message.append("SOS ID: ").append(report.getShortId()).append(" for status tracking.");
        message.append("\n\nThis is an automated emergency alert. Please contact emergency services if needed.");

        return message.toString();
    }

    /**
     * Subscribe to specific topics for targeted notifications
     */
    public void subscribeToTopic(String topic) {
        try {
            JSONObject tags = new JSONObject();
            tags.put(topic, true);
            OneSignal.sendTags(tags);
            Log.d(TAG, "Subscribed to topic: " + topic);
        } catch (JSONException e) {
            Log.e(TAG, "Error subscribing to topic", e);
        }
    }

    /**
     * Unsubscribe from a topic
     */
    public void unsubscribeFromTopic(String topic) {
        OneSignal.deleteTag(topic);
        Log.d(TAG, "Unsubscribed from topic: " + topic);
    }
}