package com.rescuereach.service.notification;

import android.content.Context;
import android.util.Log;

import com.rescuereach.RescueReachApplication;
import com.rescuereach.data.model.EmergencyContact;
import com.rescuereach.data.model.SOSReport;
import com.rescuereach.service.auth.UserSessionManager;
import com.rescuereach.service.messaging.DirectSmsManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class for sending different types of notifications
 * Bridge between emergency reporting and notification systems
 */
public class NotificationHelper {
    private static final String TAG = "NotificationHelper";

    private final Context context;
    private final OneSignalManager oneSignalManager;
    private final DirectSmsManager directSmsManager;
    private final UserSessionManager sessionManager;

    /**
     * Constructor initializes the notification helper
     * @param context Application context
     */
    public NotificationHelper(Context context) {
        this.context = context.getApplicationContext();
        this.sessionManager = UserSessionManager.getInstance(context);

        // Get OneSignal manager from application
        RescueReachApplication app = (RescueReachApplication) context.getApplicationContext();
        this.oneSignalManager = app.getOneSignalManager();

        // Initialize Direct SMS Manager for SMS notifications
        this.directSmsManager = new DirectSmsManager(context);
    }

    /**
     * Send SOS notification to responders
     * This is called by OnlineSOSManager to alert responders
     *
     * @param report The SOS report to notify about
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
        data.put("category", "emergency");
        data.put("clickAction", "OPEN_SOS_DETAILS");

        // Format the title and message
        String title = getEmergencyTypeLabel(report.getEmergencyType()) + " Emergency Alert";
        String message = "Emergency reported";

        // Add location information if available
        if (report.getAddress() != null) {
            message += " at " + report.getAddress();
            if (report.getCity() != null) {
                message += ", " + report.getCity();
            }
        }

        /*
         * In a production app, responder notification would be handled by a server component
         * which would determine which responders should receive the alert based on:
         * 1. Location proximity
         * 2. Emergency type (medical, fire, police)
         * 3. Responder availability status
         */

        // Log the action for now
        Log.d(TAG, "Server would notify responders about: " + title + ": " + message);

        // If we're testing with direct notification to the reporter (for demo purposes only)
        if (oneSignalManager != null && report.getUserId() != null && !report.getUserId().isEmpty()) {
            try {
                data.put("type", "responder_notification");
                oneSignalManager.sendPushNotification(report.getUserId(), title, message, data);
            } catch (Exception e) {
                Log.e(TAG, "Error sending test push notification", e);
            }
        }
    }

    /**
     * Send emergency SMS to emergency contacts
     * Called by OnlineSOSManager when SOS is activated
     *
     * @param report The SOS report
     * @param contacts List of emergency contacts
     */
    public void sendEmergencySMS(SOSReport report, List<EmergencyContact> contacts) {
        Log.d(TAG, "Sending emergency SMS to " + (contacts != null ? contacts.size() : 0) + " contacts");

        if (contacts == null || contacts.isEmpty()) {
            Log.w(TAG, "No emergency contacts to notify");
            return;
        }

        // Create the message
        String message = createEmergencyMessage(report);

        // Send SMS to each contact
        for (EmergencyContact contact : contacts) {
            if (contact.getPhoneNumber() != null && !contact.getPhoneNumber().isEmpty()) {
                Log.d(TAG, "Sending emergency SMS to " + contact.getName() +
                        " (" + contact.getFormattedPhoneNumber() + ")");

                try {
                    // Use Direct SMS Manager for immediate delivery
                    directSmsManager.sendMultipartSms(contact.getFormattedPhoneNumber(), message);
                } catch (Exception e) {
                    Log.e(TAG, "Error sending SMS to " + contact.getFormattedPhoneNumber(), e);
                }
            }
        }
    }

    /**
     * Notify user about SOS status change
     * Called when an SOS report's status is updated
     *
     * @param userId User ID to notify
     * @param report The SOS report
     */
    public void notifyUserAboutSOSUpdate(String userId, SOSReport report) {
        if (userId == null || userId.isEmpty() || report == null) {
            Log.w(TAG, "Invalid parameters for SOS update notification");
            return;
        }

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
        data.put("category", "emergency");

        // Send notification via OneSignal
        if (oneSignalManager != null) {
            try {
                oneSignalManager.sendPushNotification(userId, title, message, data);
            } catch (Exception e) {
                Log.e(TAG, "Error sending push notification for status update", e);
            }
        }
    }

    /**
     * Send notification to all emergency contacts about SOS status update
     * Called when an SOS report's status is updated
     *
     * @param report The SOS report
     * @param status New status
     */
    public void notifyEmergencyContactsAboutUpdate(SOSReport report, String status) {
        List<EmergencyContact> contacts = report.getEmergencyContacts();
        if (contacts == null || contacts.isEmpty()) {
            return;
        }

        // Create status update message
        String message = createStatusUpdateMessage(report, status);

        // Send SMS to each emergency contact
        for (EmergencyContact contact : contacts) {
            if (contact.getPhoneNumber() != null && !contact.getPhoneNumber().isEmpty()) {
                try {
                    directSmsManager.sendSms(contact.getFormattedPhoneNumber(), message);
                } catch (Exception e) {
                    Log.e(TAG, "Error sending status update SMS to " +
                            contact.getFormattedPhoneNumber(), e);
                }
            }
        }
    }

    /**
     * Create emergency message for SMS
     * @param report The SOS report
     * @return Formatted emergency message
     */
    private String createEmergencyMessage(SOSReport report) {
        StringBuilder message = new StringBuilder();
        message.append("EMERGENCY ALERT: ");
        message.append(report.getUserFullName()).append(" has reported a ");
        message.append(getEmergencyTypeLabel(report.getEmergencyType())).append(" emergency.\n\n");

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
        if (report.getPrimaryEmergencyService() != null) {
            message.append("Nearest emergency service: ");
            message.append(report.getPrimaryEmergencyService().getName());
            if (report.getPrimaryEmergencyService().getDistance() > 0) {
                message.append(" (").append(report.getPrimaryEmergencyService().getFormattedDistance()).append(")");
            }
            message.append("\n");

            // Add emergency number if available
            String emergencyNumber = report.getPrimaryEmergencyService().getEmergencyNumber();
            if (emergencyNumber != null && !emergencyNumber.isEmpty()) {
                message.append("Emergency #: ").append(emergencyNumber);
                message.append("\n\n");
            }
        } else {
            message.append("\n");
        }

        // Add SOS ID and app information
        message.append("SOS ID: ").append(report.getShortId());
        message.append("\n\nThis is an automated emergency alert from RescueReach.");

        // Add tracking URL if available - this would be handled by your backend
        message.append("\nTrack status at: https://rescuereach.com/sos/").append(report.getReportId());

        return message.toString();
    }

    /**
     * Create status update message for SMS
     * @param report The SOS report
     * @param status New status
     * @return Formatted status update message
     */
    private String createStatusUpdateMessage(SOSReport report, String status) {
        StringBuilder message = new StringBuilder();
        message.append("SOS STATUS UPDATE: ");

        // Customize message based on status
        switch (status) {
            case "RECEIVED":
                message.append("The ").append(getEmergencyTypeLabel(report.getEmergencyType()))
                        .append(" emergency reported by ").append(report.getUserFullName())
                        .append(" has been received by our system.");
                break;
            case "RESPONDING":
                message.append("Help is on the way to ").append(report.getUserFullName())
                        .append("'s location for the reported ").append(getEmergencyTypeLabel(report.getEmergencyType()))
                        .append(" emergency.");
                break;
            case "RESOLVED":
                message.append("The ").append(getEmergencyTypeLabel(report.getEmergencyType()))
                        .append(" emergency reported by ").append(report.getUserFullName())
                        .append(" has been marked as resolved.");
                break;
            default:
                message.append("The status of ").append(report.getUserFullName())
                        .append("'s emergency has changed to ").append(status).append(".");
        }

        // Add SOS ID and tracking URL
        message.append("\n\nSOS ID: ").append(report.getShortId());
        message.append("\nTrack status at: https://rescuereach.com/sos/").append(report.getReportId());
        message.append("\n\nThis is an automated message from RescueReach.");

        return message.toString();
    }

    /**
     * Get a user-friendly label for emergency type
     */
    private String getEmergencyTypeLabel(String emergencyType) {
        if (emergencyType == null) return "General";

        switch (emergencyType.toUpperCase()) {
            case "POLICE":
                return "Police";
            case "FIRE":
                return "Fire";
            case "MEDICAL":
                return "Medical";
            default:
                return emergencyType;
        }
    }
}