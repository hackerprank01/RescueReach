//package com.rescuereach.service.notification;
//
//import android.content.Context;
//import android.util.Log;
//
//import com.rescuereach.RescueReachApplication;
//import com.rescuereach.data.model.EmergencyContact;
//import com.rescuereach.data.model.SOSReport;
//import com.rescuereach.service.messaging.DirectSmsManager;
//
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
///**
// * Helper class for sending different types of notifications
// */
//public class NotificationHelper {
//    private static final String TAG = "NotificationHelper";
//
//    private final Context context;
//    private final OneSignalManager oneSignalManager;
//    private final DirectSmsManager directSmsManager;
//
//    public NotificationHelper(Context context) {
//        this.context = context.getApplicationContext();
//        this.oneSignalManager = ((RescueReachApplication) context.getApplicationContext())
//                .getOneSignalManager();
//        this.directSmsManager = new DirectSmsManager(context);
//    }
//
//    /**
//     * Send SOS notification to responders
//     */
//    public void notifyResponders(SOSReport report) {
//        Log.d(TAG, "Notifying responders about SOS: " + report.getReportId());
//
//        // Create data for the notification
//        Map<String, String> data = new HashMap<>();
//        data.put("reportId", report.getReportId());
//        data.put("emergencyType", report.getEmergencyType());
//        data.put("latitude", String.valueOf(report.getLatitude()));
//        data.put("longitude", String.valueOf(report.getLongitude()));
//        data.put("userId", report.getUserId());
//
//        // Format the title and message
//        String title = report.getEmergencyType() + " Emergency Alert";
//        String message = "Emergency reported at " + report.getAddress();
//        if (report.getCity() != null) {
//            message += ", " + report.getCity();
//        }
//
//        // NOTE: In a real app, you would have:
//        // 1. A server-side component to send this to responders
//        // 2. Use OneSignal segments/filters to target responders by region/type
//
//        // For now, just log this
//        Log.d(TAG, "Server would send notification to responders: " + title + ": " + message);
//    }
//
//    /**
//     * Send emergency SMS to emergency contacts
//     */
//    public void sendEmergencySMS(SOSReport report, List<EmergencyContact> contacts) {
//        Log.d(TAG, "Sending emergency SMS to " + contacts.size() + " contacts");
//
//        // Create the message
//        String message = createEmergencyMessage(report);
//
//        // Send SMS to each contact
//        for (EmergencyContact contact : contacts) {
//            if (contact.getPhoneNumber() != null && !contact.getPhoneNumber().isEmpty()) {
//                directSmsManager.sendMultipartSms(contact.getFormattedPhoneNumber(), message);
//            }
//        }
//    }
//
//    /**
//     * Notify user about SOS status change
//     */
//    public void notifyUserAboutSOSUpdate(String userId, SOSReport report) {
//        Log.d(TAG, "Notifying user about SOS update: " + report.getStatus());
//
//        String title = "SOS Status Update";
//        String message;
//
//        // Customize message based on status
//        switch (report.getStatus()) {
//            case "RECEIVED":
//                message = "Your emergency has been received by our system";
//                break;
//            case "RESPONDING":
//                message = "Help is on the way to your location";
//                break;
//            case "RESOLVED":
//                message = "Your emergency has been marked as resolved";
//                break;
//            default:
//                message = "Your SOS report status has changed to " + report.getStatus();
//        }
//
//        // Create data for the notification
//        Map<String, String> data = new HashMap<>();
//        data.put("reportId", report.getReportId());
//        data.put("status", report.getStatus());
//        data.put("clickAction", "OPEN_SOS_DETAILS");
//
//        // Send notification via OneSignal
//        if (oneSignalManager != null) {
//            oneSignalManager.sendPushNotification(userId, title, message, data);
//        }
//    }
//
//    /**
//     * Create emergency message for SMS
//     */
//    private String createEmergencyMessage(SOSReport report) {
//        StringBuilder message = new StringBuilder();
//        message.append("EMERGENCY ALERT: ");
//        message.append(report.getUserFullName()).append(" has reported a ");
//        message.append(report.getEmergencyType()).append(" emergency.\n\n");
//
//        // Add location details
//        if (report.getAddress() != null) {
//            message.append("Location: ").append(report.getAddress());
//            if (report.getCity() != null) message.append(", ").append(report.getCity());
//            if (report.getState() != null) message.append(", ").append(report.getState());
//            message.append("\n\n");
//        } else {
//            // Include coordinates if no address
//            message.append(String.format("Location coordinates: %.6f, %.6f\n\n",
//                    report.getLatitude(), report.getLongitude()));
//        }
//
//        // Add emergency service info if available
//        if (report.getPrimaryEmergencyService() != null) {
//            message.append("Nearest emergency service: ");
//            message.append(report.getPrimaryEmergencyService().getName());
//            message.append("\n");
//
//            // Add emergency number if available
//            String emergencyNumber = report.getPrimaryEmergencyService().getEmergencyNumber();
//            if (emergencyNumber != null && !emergencyNumber.isEmpty()) {
//                message.append("Emergency #: ").append(emergencyNumber);
//                message.append("\n\n");
//            }
//        }
//
//        // Add SOS ID
//        message.append("SOS ID: ").append(report.getShortId());
//        message.append("\n\nThis is an automated emergency alert from RescueReach.");
//
//        return message.toString();
//    }
//}