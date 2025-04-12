//package com.rescuereach.service.notification;
//
//import android.content.Context;
//import android.util.Log;
//import androidx.annotation.NonNull;
//import com.onesignal.OneSignal;
////import com.onesignal.OSNotification;
////import com.onesignal.OSNotificationReceivedEvent;
////import com.onesignal.OSDeviceState;
//import org.json.JSONArray;
//import org.json.JSONException;
//import org.json.JSONObject;
//import java.util.Map;
//
///**
// * Manager class for handling OneSignal push notifications integration
// */
//public class OneSignalManager {
//    private static final String TAG = "OneSignalManager";
//
//    private final Context context;
//    private String oneSignalUserId;
//
//    public OneSignalManager(Context context) {
//        this.context = context.getApplicationContext();
//
//        // Set up notification handlers
//        setupNotificationHandlers();
//
//        // Get the OneSignal user ID
//        OSDeviceState deviceState = OneSignal.getDeviceState();
//        if (deviceState != null) {
//            oneSignalUserId = deviceState.getUserId();
//            Log.d(TAG, "OneSignal User ID: " + oneSignalUserId);
//        }
//    }
//
//    /**
//     * Set up notification handlers for OneSignal
//     */
//    private void setupNotificationHandlers() {
//        // Set notification will show in foreground handler
//        OneSignal.setNotificationWillShowInForegroundHandler(this::handleNotificationReceived);
//    }
//
//    /**
//     * Handle notification received in foreground
//     */
//    private void handleNotificationReceived(OSNotificationReceivedEvent notificationReceivedEvent) {
//        OSNotification notification = notificationReceivedEvent.getNotification();
//
//        // Get notification data
//        String title = notification.getTitle();
//        String body = notification.getBody();
//        JSONObject data = notification.getAdditionalData();
//
//        Log.d(TAG, "Notification Received - Title: " + title + ", Body: " + body +
//                ", Data: " + (data != null ? data.toString() : "null"));
//
//        // Complete processing (required)
//        notificationReceivedEvent.complete(notification);
//    }
//
//    /**
//     * Set the user ID for OneSignal (for targeting notifications)
//     */
//    public void setUserId(String userId) {
//        if (userId != null && !userId.isEmpty()) {
//            Log.d(TAG, "Setting OneSignal external user ID: " + userId);
//            OneSignal.setExternalUserId(userId);
//        }
//    }
//
//    /**
//     * Clear user ID when logging out
//     */
//    public void clearUserId() {
//        OneSignal.removeExternalUserId();
//    }
//
//    /**
//     * Get the OneSignal user ID (for targeting this device)
//     */
//    public String getOneSignalUserId() {
//        if (oneSignalUserId == null) {
//            OSDeviceState deviceState = OneSignal.getDeviceState();
//            if (deviceState != null) {
//                oneSignalUserId = deviceState.getUserId();
//            }
//        }
//        return oneSignalUserId;
//    }
//
//    /**
//     * Send a push notification to a specific user
//     * Note: In production, this should be done from your server
//     */
//    public void sendPushNotification(String targetUserId, String title, String message, Map<String, String> data) {
//        try {
//            Log.d(TAG, "Sending push notification to: " + targetUserId);
//
//            // Create notification content JSON
//            JSONObject notificationContent = new JSONObject();
//
//            // Add title and message
//            JSONObject headings = new JSONObject();
//            headings.put("en", title);
//
//            JSONObject contents = new JSONObject();
//            contents.put("en", message);
//
//            notificationContent.put("headings", headings);
//            notificationContent.put("contents", contents);
//
//            // Add custom data if provided
//            if (data != null && !data.isEmpty()) {
//                JSONObject dataObj = new JSONObject();
//                for (Map.Entry<String, String> entry : data.entrySet()) {
//                    dataObj.put(entry.getKey(), entry.getValue());
//                }
//                notificationContent.put("data", dataObj);
//            }
//
//            // Target specific user by external user ID
//            JSONArray includedExternalUserIds = new JSONArray();
//            includedExternalUserIds.put(targetUserId);
//            notificationContent.put("include_external_user_ids", includedExternalUserIds);
//
//            // Send the notification
//            OneSignal.postNotification(notificationContent, null);
//
//        } catch (JSONException e) {
//            Log.e(TAG, "Error creating notification JSON", e);
//        }
//    }
//
//    /**
//     * Subscribe to specific topics for targeted notifications
//     */
//    public void subscribeToTopic(String topic) {
//        try {
//            JSONObject tags = new JSONObject();
//            tags.put(topic, true);
//            OneSignal.sendTags(tags);
//            Log.d(TAG, "Subscribed to topic: " + topic);
//        } catch (JSONException e) {
//            Log.e(TAG, "Error subscribing to topic", e);
//        }
//    }
//
//    /**
//     * Unsubscribe from a topic
//     */
//    public void unsubscribeFromTopic(String topic) {
//        OneSignal.deleteTag(topic);
//        Log.d(TAG, "Unsubscribed from topic: " + topic);
//    }
//}