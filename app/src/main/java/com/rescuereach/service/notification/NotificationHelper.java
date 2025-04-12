package com.rescuereach.service.notification;

import android.content.Context;
import android.util.Log;

import com.rescuereach.RescueReachApplication;
import com.rescuereach.data.model.EmergencyContact;
import com.rescuereach.data.model.SOSReport;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class for sending different types of notifications
 */
public class NotificationHelper {
    private static final String TAG = "NotificationHelper";

    private final Context context;
    private final OneSignalManager oneSignalManager;

    public NotificationHelper(Context context) {
        this.context = context.getApplicationContext();
        this.oneSignalManager = ((RescueReachApplication) context.getApplicationContext())
                .getOneSignalManager();
    }

    /**
     * Send SOS notification to responders
     */
    public void notifyResponders(SOSReport report) {
        Log.d(TAG, "Notifying responders about SOS: " + report.getReportId());

        // Create data for the notification
        Map<String, String> data = new HashMap<>();
        data.put("reportId", report.getReportId());
        data.put("emergencyType", report.getEmergencyType());
        data.put("latitude", String.valueOf(report.getLatitude()));
        data.put("longitude", String.valueOf(report.getLongitude()));
        data.put("userId", report.getUserId());

        // Format the title and message
        String title = report.getEmergencyType() + " Emergency Alert";
        String message = "Emergency reported at " + report.getAddress();
        if (report.getCity() != null) {
            message += ", " + report.getCity();
        }

        // NOTE: In a real app, you need to:
        // 1. Have a server-side component to send this to responders
        // 2. Use OneSignal segments/filters to target responders by region/type

        // For now, just log this - server implementation needed for responder targeting
        Log.d(TAG, "Server would send notification to responders: " + title + ": " + message);
    }

    /**
     * Send emergency SMS to emergency contacts
     */
    public void sendEmergencySMS(SOSReport report, List<EmergencyContact> contacts) {
        Log.d(TAG, "Sending emergency SMS to " + contacts.size() + " contacts");

        // Use OneSignal manager to send SMS via Android SMS API
        oneSignalManager.sendEmergencySMS(report, contacts);
    }

    /**
     * Notify user about SOS status change
     */
    public void notifyUserAboutSOSUpdate(String userId, SOSReport report) {
        Log.d(TAG, "Notifying user about SOS update: " + report.getStatus());

        String title = "SOS Status Update";
        String message;

        // Customize message based on status
        switch (report.getStatus()) {
            case "RECEIVED":
                message = "Your emergency has been received by our system";
                break;
            case "RESPONDING":
                message = "Help is on the way to your location";
                break;
            case "RESOLVED":
                message = "Your emergency has been marked as resolved";
                break;
            default:
                message = "Your SOS report status has changed to " + report.getStatus();
        }

        // Create data for the notification
        Map<String, String> data = new HashMap<>();
        data.put("reportId", report.getReportId());
        data.put("status", report.getStatus());
        data.put("clickAction", "OPEN_SOS_DETAILS");

        // Send notification
        oneSignalManager.sendPushNotification(userId, title, message, data);
    }
}