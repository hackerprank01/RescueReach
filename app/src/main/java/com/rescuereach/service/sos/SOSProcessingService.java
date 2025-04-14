package com.rescuereach.service.sos;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;
import android.util.Log;

import com.rescuereach.R;
import com.rescuereach.RescueReachApplication;
import com.rescuereach.data.model.SOSReport;
import com.rescuereach.data.repository.OnCompleteListener;
import com.rescuereach.data.repository.RepositoryProvider;
import com.rescuereach.data.repository.SOSRepository;
import com.rescuereach.service.auth.UserSessionManager;
import com.rescuereach.service.notification.NotificationService;
import com.rescuereach.service.notification.NotificationTemplates;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Service for processing SOS emergency reports
 * Handles saving reports to Firebase, sending notifications, and SMS fallback
 */
public class SOSProcessingService {
    private static final String TAG = "SOSProcessingService";

    private final Context context;
    private final SOSRepository sosRepository;
    private final NotificationService notificationService;
    private final UserSessionManager sessionManager;
    private final Executor backgroundExecutor;
    private final Handler mainHandler;

    // Constants for SMS messages
    private static final int MAX_SMS_LENGTH = 160;

    /**
     * Create a new SOS Processing Service
     * @param context Application context
     */
    public SOSProcessingService(Context context) {
        this.context = context.getApplicationContext();
        this.sosRepository = RepositoryProvider.getSOSRepository();
        this.notificationService = ((RescueReachApplication) context.getApplicationContext())
                .getNotificationService();
        this.sessionManager = UserSessionManager.getInstance(context);
        this.backgroundExecutor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Process an SOS report - the main entry point for this service
     * @param report The SOS report to process
     * @param listener Callback for processing result
     */
    public void processSOSReport(SOSReport report, SOSProcessingListener listener) {
        Log.d(TAG, "Processing SOS report for " + report.getEmergencyType());

        if (report == null) {
            if (listener != null) {
                listener.onProcessingFailed("Invalid SOS report data");
            }
            return;
        }

        // Set user ID if not already set
        if ((report.getUserId() == null || report.getUserId().isEmpty()) && sessionManager != null) {
            report.setUserId(sessionManager.getSavedPhoneNumber());
        }

        // Set timestamp if not already set
        if (report.getTimestamp() == null) {
            report.setTimestamp(new Date());
        }

        // Set initial status
        if (report.getStatus() == null || report.getStatus().isEmpty()) {
            report.setStatus(SOSReport.STATUS_PENDING);
        }

        // Process based on network availability
        if (report.isOnline()) {
            processOnlineReport(report, listener);
        } else {
            processOfflineReport(report, listener);
        }
    }

    /**
     * Process an SOS report when online
     */
    private void processOnlineReport(SOSReport report, SOSProcessingListener listener) {
        Log.d(TAG, "Processing online SOS report");

        // Save to Firebase
        sosRepository.saveSOSReport(report, new SOSRepository.OnReportSavedListener() {
            @Override
            public void onSuccess(SOSReport savedReport) {
                Log.d(TAG, "SOS report saved with ID: " + savedReport.getReportId());

                // Send notifications
                sendEmergencyNotifications(savedReport);

                // Send SMS to emergency contacts if needed
                sendEmergencyContactSMS(savedReport);

                // Report success
                if (listener != null) {
                    listener.onProcessingComplete(savedReport);
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Failed to save SOS report", e);

                // Fall back to offline processing if Firebase save fails
                processOfflineReport(report, listener);
            }
        });
    }

    /**
     * Process an SOS report when offline
     */
    private void processOfflineReport(SOSReport report, SOSProcessingListener listener) {
        Log.d(TAG, "Processing offline SOS report");

        // In offline mode, we can only send SMS
        boolean smsSent = sendEmergencyContactSMS(report);

        // Update status based on SMS result
        report.setSmsSent(smsSent);
        report.setSmsStatus(smsSent ? "SENT" : "FAILED");

        if (smsSent) {
            Log.d(TAG, "Offline SOS report processed - SMS sent");
            report.setStatus(SOSReport.STATUS_PENDING);

            // Create local notification to confirm SMS was sent
            // This will be shown even without internet
            createLocalNotification(report);

            if (listener != null) {
                listener.onProcessingComplete(report);
            }
        } else {
            Log.e(TAG, "Offline SOS report failed - SMS sending failed");
            if (listener != null) {
                listener.onProcessingFailed("Failed to send emergency SMS");
            }
        }

        // Cache the report for later sync when back online
        cacheReportForLaterSync(report);
    }

    /**
     * Send emergency notifications to responders
     */
    private void sendEmergencyNotifications(SOSReport report) {
        if (notificationService == null) {
            Log.e(TAG, "Cannot send notifications: NotificationService is null");
            return;
        }

        try {
            // Create notification payload
            JSONObject payload;
            String emergencyType = report.getEmergencyType();

            switch (emergencyType) {
                case "POLICE":
                    payload = NotificationTemplates.createPoliceEmergencyPayload(context, report);
                    break;
                case "FIRE":
                    payload = NotificationTemplates.createFireEmergencyPayload(context, report);
                    break;
                case "MEDICAL":
                    payload = NotificationTemplates.createMedicalEmergencyPayload(context, report);
                    break;
                default:
                    // Use police as default for unknown types
                    payload = NotificationTemplates.createPoliceEmergencyPayload(context, report);
                    break;
            }

            // Send notification to appropriate responders
            // In a real implementation this would target specific user segments
            Log.d(TAG, "Notification payload created for " + emergencyType);

            // If this app supports responders directly, you would send OneSignal
            // notifications here using the REST API with proper targeting

            // TODO: Send to backend service that handles responder notifications
            // For now, just log that we would send it
            Log.d(TAG, "Would send notification to responders: " + payload.toString());

        } catch (Exception e) {
            Log.e(TAG, "Error creating notification payload", e);
        }
    }

    /**
     * Send SMS to emergency contacts
     * @return true if SMS was sent, false otherwise
     */
    private boolean sendEmergencyContactSMS(SOSReport report) {
        // Get emergency contacts from the report
        List<String> contactNumbers = report.getEmergencyContactNumbers();

        if (contactNumbers == null || contactNumbers.isEmpty()) {
            Log.w(TAG, "No emergency contacts available for SMS");
            return false;
        }

        boolean allSuccess = true;

        // Format the emergency message
        String emergencyMessage = formatEmergencyMessage(report);

        try {
            SmsManager smsManager = SmsManager.getDefault();

            for (String phoneNumber : contactNumbers) {
                if (phoneNumber == null || phoneNumber.isEmpty()) {
                    continue;
                }

                try {
                    // For long messages, divide them into parts
                    if (emergencyMessage.length() > MAX_SMS_LENGTH) {
                        ArrayList<String> messageParts = smsManager.divideMessage(emergencyMessage);
                        smsManager.sendMultipartTextMessage(
                                phoneNumber, null, messageParts, null, null);
                    } else {
                        smsManager.sendTextMessage(
                                phoneNumber, null, emergencyMessage, null, null);
                    }

                    Log.d(TAG, "SMS sent to emergency contact: " + phoneNumber);

                } catch (Exception e) {
                    Log.e(TAG, "Failed to send SMS to: " + phoneNumber, e);
                    allSuccess = false;
                }
            }

            // Update report with SMS status
            report.setSmsSent(allSuccess);
            report.setSmsStatus(allSuccess ? "SENT" : "PARTIAL");

            return allSuccess;

        } catch (Exception e) {
            Log.e(TAG, "Error sending emergency SMS", e);
            report.setSmsSent(false);
            report.setSmsStatus("FAILED");
            return false;
        }
    }

    /**
     * Format emergency message for SMS
     */
    private String formatEmergencyMessage(SOSReport report) {
        String emergencyType = report.getEmergencyType();
        String location = "Unknown location";

        // Format location string
        if (report.getAddress() != null && !report.getAddress().isEmpty()) {
            location = report.getAddress();

            if (report.getCity() != null && !report.getCity().equals("Unknown")) {
                if (!location.contains(report.getCity())) {
                    location += ", " + report.getCity();
                }

                if (report.getState() != null && !report.getState().equals("Unknown")) {
                    if (!location.contains(report.getState())) {
                        location += ", " + report.getState();
                    }
                }
            }
        } else if (report.getLocation() != null) {
            // Use coordinates if no address available
            location = String.format(Locale.US, "%.6f, %.6f",
                    report.getLocation().getLatitude(),
                    report.getLocation().getLongitude());
            location += " (coordinates)";
        }

        // Get the user's name from the report if available
        String userName = "A person";
        if (report.getUserInfo() != null && report.getUserInfo().containsKey("name")) {
            Object name = report.getUserInfo().get("name");
            if (name != null && !name.toString().isEmpty()) {
                userName = name.toString();
            }
        } else if (sessionManager != null && sessionManager.getFullName() != null) {
            userName = sessionManager.getFullName();
        }

        // Format message based on emergency type
        String messageTemplate;
        switch (emergencyType) {
            case "POLICE":
                messageTemplate = "EMERGENCY ALERT: %s needs police assistance at %s. " +
                        "Please contact emergency services. This alert was sent via RescueReach.";
                break;
            case "FIRE":
                messageTemplate = "EMERGENCY ALERT: %s reported a fire emergency at %s. " +
                        "Please contact fire services. This alert was sent via RescueReach.";
                break;
            case "MEDICAL":
                messageTemplate = "EMERGENCY ALERT: %s needs medical assistance at %s. " +
                        "Please contact medical services. This alert was sent via RescueReach.";
                break;
            default:
                messageTemplate = "EMERGENCY ALERT: %s needs assistance at %s. " +
                        "Please contact emergency services. This alert was sent via RescueReach.";
                break;
        }

        return String.format(messageTemplate, userName, location);
    }

    /**
     * Create a local notification for the user when offline
     */
    private void createLocalNotification(SOSReport report) {
        // TODO: Create a local notification to inform the user about SMS status
        // This would typically be implemented with NotificationCompat.Builder
        // For now, we'll just log that this would happen
        Log.d(TAG, "Would create local notification for offline SOS");
    }

    /**
     * Cache report for later synchronization when back online
     */
    private void cacheReportForLaterSync(SOSReport report) {
        // TODO: Implement caching using Room database for offline persistence
        // This would allow the app to sync reports when back online
        // For now, we'll just log that this would happen
        Log.d(TAG, "Would cache SOS report for later sync");
    }

    /**
     * Update the status of an SOS report
     */
    public void updateSOSStatus(String reportId, String newStatus, SOSStatusUpdateListener listener) {
        if (reportId == null || reportId.isEmpty() || newStatus == null || newStatus.isEmpty()) {
            if (listener != null) {
                listener.onStatusUpdateFailed("Invalid report ID or status");
            }
            return;
        }

        // Create responder info if this is a responder updating status
        Map<String, Object> responderInfo = null;
        if (sessionManager != null && sessionManager.isVolunteer()) {
            responderInfo = new HashMap<>();
            responderInfo.put("responderId", sessionManager.getSavedPhoneNumber());
            responderInfo.put("responderName", sessionManager.getFullName());
            responderInfo.put("respondedAt", new Date());
        }

        sosRepository.updateSOSStatus(reportId, newStatus, responderInfo,
                new OnCompleteListener() {
                    @Override
                    public void onSuccess() {
                        // Get updated report to send status notification
                        sosRepository.getSOSReportById(reportId, new SOSRepository.OnReportFetchedListener() {
                            @Override
                            public void onSuccess(SOSReport report) {
                                // Send status update notification
                                sendStatusUpdateNotification(report);

                                if (listener != null) {
                                    listener.onStatusUpdated(report);
                                }
                            }

                            @Override
                            public void onError(Exception e) {
                                Log.e(TAG, "Error fetching updated report", e);

                                // Still consider update successful since status was changed
                                if (listener != null) {
                                    listener.onStatusUpdated(null);
                                }
                            }
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Error updating SOS status", e);
                        if (listener != null) {
                            listener.onStatusUpdateFailed(e.getMessage());
                        }
                    }
                });
    }

    /**
     * Send status update notification to the user who reported the emergency
     */
    private void sendStatusUpdateNotification(SOSReport report) {
        if (report == null || notificationService == null) {
            Log.e(TAG, "Cannot send status notification: Report or NotificationService is null");
            return;
        }

        try {
            // Create status update notification
            JSONObject payload = NotificationTemplates.createStatusUpdatePayload(context, report);

            // In a real implementation, this would target the specific user who created the report
            // using the OneSignal REST API with external user ID targeting

            // For now, just log that we would send it
            Log.d(TAG, "Would send status notification to user: " + payload.toString());

        } catch (Exception e) {
            Log.e(TAG, "Error creating status notification payload", e);
        }
    }

    /**
     * Interface for SOS processing callbacks
     */
    public interface SOSProcessingListener {
        void onProcessingComplete(SOSReport report);
        void onProcessingFailed(String errorMessage);
    }

    /**
     * Interface for SOS status update callbacks
     */
    public interface SOSStatusUpdateListener {
        void onStatusUpdated(SOSReport report);
        void onStatusUpdateFailed(String errorMessage);
    }
}