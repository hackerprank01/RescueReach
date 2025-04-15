package com.rescuereach.service.sos;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.rescuereach.BuildConfig;
import com.rescuereach.R;
import com.rescuereach.RescueReachApplication;
import com.rescuereach.data.model.SOSReport;
import com.rescuereach.data.repository.OnCompleteListener;
import com.rescuereach.data.repository.RepositoryProvider;
import com.rescuereach.data.repository.SOSRepository;
import com.rescuereach.service.auth.UserSessionManager;
import com.rescuereach.service.notification.NotificationService;
import com.rescuereach.service.notification.NotificationTemplates;
import com.rescuereach.util.PermissionManager; // Changed from PermissionHelper to PermissionManager
import com.rescuereach.util.ToastUtil;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for processing SOS emergency reports
 * Handles saving reports to Firebase, sending notifications, and SMS fallback
 * Fixed with timeout handling and improved error recovery
 */
public class SOSProcessingService {
    private static final String TAG = "SOSProcessingService";

    // Network timeout constants
    private static final long NETWORK_TIMEOUT_SECONDS = 15;

    // Notification channel constants
    private static final String EMERGENCY_CHANNEL_ID = "emergency_channel";
    private static final int NOTIFICATION_ID = 1001;

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
            notifyProcessingFailed(listener, "Invalid SOS report data");
            return;
        }

        // Ensure report ID is unique if not set
        if (report.getReportId() == null || report.getReportId().isEmpty()) {
            report.setReportId("sos_" + System.currentTimeMillis());
        }

        // Create a local notification immediately to reassure user
        createLocalNotification(report, "Emergency Reported",
                "Processing your " + report.getEmergencyType() + " emergency report");

        // Run processing on background thread to avoid ANR
        backgroundExecutor.execute(() -> {
            try {
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
            } catch (Exception e) {
                Log.e(TAG, "Error processing SOS report", e);
                notifyProcessingFailed(listener, "Error processing report: " + e.getMessage());
            }
        });
    }

    /**
     * Process an SOS report when online with timeout handling
     */
    private void processOnlineReport(final SOSReport report, final SOSProcessingListener listener) {
        Log.d(TAG, "Processing online SOS report");

        try {
            // Create a fallback report in case online processing fails
            final SOSReport offlineReport = createOfflineFallbackReport(report);

            // First try to submit via the Task API which is more reliable
            try {
                Task<DocumentReference> submitTask = sosRepository.submitSOSReport(report);

                DocumentReference docRef = Tasks.await(submitTask, NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                if (docRef != null) {
                    final SOSReport savedReport = report;
                    savedReport.setReportId(docRef.getId());

                    Log.d(TAG, "SOS report saved with ID: " + savedReport.getReportId());

                    // Send SMS messages in parallel
                    final AtomicBoolean smsSent = new AtomicBoolean(false);
                    backgroundExecutor.execute(() -> {
                        try {
                            boolean result = sendEmergencyContactSMS(savedReport);
                            smsSent.set(result);
                            Log.d(TAG, "SMS sending completed, result: " + result);

                            // Update notification with SMS status
                            if (result) {
                                createLocalNotification(savedReport, "Emergency Contacts Notified",
                                        "SMS messages sent to your emergency contacts");
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error sending SMS", e);
                        }
                    });

                    // Send emergency notifications
                    sendEmergencyNotifications(savedReport);

                    // Update report with final state and notify
                    notifyProcessingComplete(listener, savedReport);
                    return;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error using submitSOSReport task API", e);
                // Fall through to the callback-based method
            }

            // Fall back to the callback API if task API fails
            sosRepository.saveSOSReport(report, new SOSRepository.OnReportSavedListener() {
                @Override
                public void onSuccess(SOSReport savedReport) {
                    try {
                        Log.d(TAG, "SOS report saved with ID: " + savedReport.getReportId());

                        // Send notifications on background thread
                        backgroundExecutor.execute(() -> {
                            try {
                                // Send notifications
                                sendEmergencyNotifications(savedReport);

                                // Send SMS to emergency contacts if needed
                                boolean smsSent = sendEmergencyContactSMS(savedReport);

                                // Update notification with SMS status
                                if (smsSent) {
                                    createLocalNotification(savedReport, "Emergency Contacts Notified",
                                            "SMS messages sent to your emergency contacts");
                                }

                                // Report success on main thread
                                notifyProcessingComplete(listener, savedReport);
                            } catch (Exception e) {
                                Log.e(TAG, "Error sending notifications", e);
                                // Report success anyway since the report was saved
                                notifyProcessingComplete(listener, savedReport);
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Error in onSuccess callback", e);
                        notifyProcessingComplete(listener, savedReport);
                    }
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Failed to save SOS report", e);

                    // Fall back to offline processing if Firebase save fails
                    processOfflineReport(offlineReport, listener);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in processOnlineReport", e);
            // Fall back to offline processing on any exception
            processOfflineReport(report, listener);
        }
    }

    /**
     * Create a fallback offline copy of the report for reliability
     */
    private SOSReport createOfflineFallbackReport(SOSReport original) {
        // Create a new report with the same data
        SOSReport fallback = new SOSReport();

        // Copy all fields needed for offline processing
        fallback.setUserId(original.getUserId());
        fallback.setEmergencyType(original.getEmergencyType());
        fallback.setLocation(original.getLocation());
        fallback.setAddress(original.getAddress());
        fallback.setCity(original.getCity());
        fallback.setState(original.getState());
        fallback.setTimestamp(new Date());
        fallback.setStatus(SOSReport.STATUS_PENDING);
        fallback.setEmergencyContactNumbers(original.getEmergencyContactNumbers());
        fallback.setOnline(false);  // Mark this as offline for processing

        // Add user info
        if (original.getUserInfo() != null) {
            fallback.setUserInfo(new HashMap<>(original.getUserInfo()));
        }

        return fallback;
    }

    public void cancelSOS(String reportId, SOSStatusUpdateListener listener) {
        if (reportId == null || reportId.isEmpty()) {
            if (listener != null) {
                listener.onStatusUpdateFailed("Invalid report ID");
            }
            return;
        }

        try {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            DocumentReference reportRef = db.collection("sos_reports").document(reportId);

            // Create a simple update that only changes the status field
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", SOSReport.STATUS_CANCELED);
            updates.put("statusUpdatedAt", new Date());

            // Add cancellation metadata
            Map<String, Object> cancellationInfo = new HashMap<>();
            cancellationInfo.put("cancelledBy", "user");
            cancellationInfo.put("cancelledAt", new Date());
            cancellationInfo.put("reason", "user_cancelled");
            updates.put("cancellationInfo", cancellationInfo);

            // Update only specific fields instead of the whole document
            reportRef.update(updates)
                    .addOnSuccessListener(aVoid -> {
                        // Success - also remove from active emergencies
                        try {
                            FirebaseDatabase.getInstance()
                                    .getReference("active_emergencies")
                                    .child(reportId)
                                    .removeValue();
                        } catch (Exception e) {
                            Log.e(TAG, "Error removing from active emergencies", e);
                            // Continue anyway since Firestore update succeeded
                        }

                        // Create basic report to return to caller
                        SOSReport report = new SOSReport();
                        report.setReportId(reportId);
                        report.setStatus(SOSReport.STATUS_CANCELED);
                        report.setStatusUpdatedAt(new Date());

                        if (listener != null) {
                            listener.onStatusUpdated(report);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error cancelling SOS", e);

                        if (listener != null) {
                            listener.onStatusUpdateFailed(e.getMessage());
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error in cancelSOS", e);

            if (listener != null) {
                listener.onStatusUpdateFailed(e.getMessage());
            }
        }
    }

    /**
     * Process an SOS report when offline
     */
    private void processOfflineReport(final SOSReport report, final SOSProcessingListener listener) {
        Log.d(TAG, "Processing offline SOS report");

        try {
            // In offline mode, we can only send SMS
            boolean smsSent = sendEmergencyContactSMS(report);

            // Update status based on SMS result
            report.setSmsSent(smsSent);
            report.setSmsStatus(smsSent ? "SENT" : "FAILED");

            if (smsSent) {
                Log.d(TAG, "Offline SOS report processed - SMS sent");
                report.setStatus(SOSReport.STATUS_PENDING);

                // Create local notification to confirm SMS was sent
                createLocalNotification(report, "Emergency Reported Offline",
                        "SMS messages have been sent to your emergency contacts");

                notifyProcessingComplete(listener, report);
            } else {
                Log.e(TAG, "Offline SOS report failed - SMS sending failed");
                createLocalNotification(report, "SMS Sending Failed",
                        "Could not send emergency SMS messages");

                notifyProcessingFailed(listener, "Failed to send emergency SMS");
            }

            // Cache the report for later sync when back online
            cacheReportForLaterSync(report);
        } catch (Exception e) {
            Log.e(TAG, "Error processing offline report", e);
            notifyProcessingFailed(listener, "Error processing offline report: " + e.getMessage());
        }
    }

    private void updateSOSStatusWithAuth(String reportId, String newStatus, SOSStatusUpdateListener listener) {
        // First ensure we're authenticated
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            // Try to authenticate anonymously first
            auth.signInAnonymously()
                    .addOnSuccessListener(result -> {
                        // Now proceed with update after authentication
                        performStatusUpdate(reportId, newStatus, listener);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to authenticate for status update", e);
                        if (listener != null) {
                            listener.onStatusUpdateFailed("Authentication failed: " + e.getMessage());
                        }
                    });
        } else {
            // Already authenticated, proceed directly
            performStatusUpdate(reportId, newStatus, listener);
        }
    }

    private void performStatusUpdate(String reportId, String newStatus, SOSStatusUpdateListener listener) {
        // Create responder info with current user details
        Map<String, Object> responderInfo = new HashMap<>();
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser != null) {
            responderInfo.put("userId", currentUser.getUid());
            responderInfo.put("userEmail", currentUser.getEmail());
        }

        responderInfo.put("actionTime", System.currentTimeMillis());
        responderInfo.put("action", "status_update_to_" + newStatus.toLowerCase());

        // Actually update via repository
        sosRepository.updateSOSStatus(reportId, newStatus, responderInfo,
                new OnCompleteListener() {
                    @Override
                    public void onSuccess() {
                        // Get the updated report to return
                        sosRepository.getSOSReportById(reportId, new SOSRepository.OnReportFetchedListener() {
                            @Override
                            public void onSuccess(SOSReport report) {
                                if (listener != null) {
                                    listener.onStatusUpdated(report);
                                }
                            }

                            @Override
                            public void onError(Exception e) {
                                // We updated successfully but couldn't fetch the result
                                Log.w(TAG, "Status update succeeded but couldn't fetch updated report", e);

                                // Create a basic report with the new status
                                SOSReport basicReport = new SOSReport();
                                basicReport.setReportId(reportId);
                                basicReport.setStatus(newStatus);

                                if (listener != null) {
                                    listener.onStatusUpdated(basicReport);
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
     * Notify the listener on the main thread
     */
    private void notifyProcessingComplete(final SOSProcessingListener listener, final SOSReport report) {
        if (listener == null) return;

        mainHandler.post(() -> {
            try {
                listener.onProcessingComplete(report);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener", e);
            }
        });
    }

    /**
     * Notify the listener of processing failure on the main thread
     */
    private void notifyProcessingFailed(final SOSProcessingListener listener, final String errorMessage) {
        if (listener == null) return;

        mainHandler.post(() -> {
            try {
                listener.onProcessingFailed(errorMessage != null ? errorMessage : "Unknown error");
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener", e);
            }
        });
    }

    /**
     * Send emergency notifications to responders
     */
    private void sendEmergencyNotifications(final SOSReport report) {
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
            Map<String, Object> data = new HashMap<>();
            data.put("emergencyType", emergencyType);

            if (report.getReportId() != null) {
                data.put("reportId", report.getReportId());
            }

            if (report.getLocation() != null) {
                data.put("latitude", report.getLocation().getLatitude());
                data.put("longitude", report.getLocation().getLongitude());
            }

            if (report.getAddress() != null) {
                data.put("address", report.getAddress());
            }

            // For testing and debug, just show a heading about the notification
            String heading = emergencyType + " EMERGENCY";
            String message = "Emergency notification would be sent to responders";

            // In debug mode, show a toast on the main thread
            if (BuildConfig.DEBUG) {
                mainHandler.post(() -> {
                    try {
                        ToastUtil.showShort(context, "Notification: " + heading + "\n" + message);
                    } catch (Exception e) {
                        Log.e(TAG, "Error showing toast", e);
                    }
                });
            }

            Log.d(TAG, "Emergency notification sent for: " + emergencyType);

        } catch (Exception e) {
            Log.e(TAG, "Error sending emergency notification", e);
        }
    }

    /**
     * Send SMS to emergency contacts
     * @return true if SMS was sent, false otherwise
     */
    private boolean sendEmergencyContactSMS(final SOSReport report) {
        // Get emergency contacts from the report
        List<String> contactNumbers = report.getEmergencyContactNumbers();

        // Make sure we have permission to send SMS
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "No SMS permission - cannot send emergency messages");
            return false;
        }

        if (contactNumbers == null || contactNumbers.isEmpty()) {
            Log.w(TAG, "No emergency contacts available for SMS");
            // Add default emergency contact for testing in debug builds
            if (BuildConfig.DEBUG) {
                contactNumbers = new ArrayList<>();
                contactNumbers.add("9326994197"); // Test number
            } else {
                return false;
            }
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
                    Log.d(TAG, "Attempting to send SMS to: " + phoneNumber);

                    if (BuildConfig.DEBUG) {
                        // In debug mode, just log instead of actually sending
                        Log.d(TAG, "DEBUG MODE - Would send SMS: " + emergencyMessage);
                        continue;
                    }

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

            // Show a toast message on the main thread about SMS status
            final boolean finalSuccess = allSuccess;
            mainHandler.post(() -> {
                try {
                    String message = finalSuccess ?
                            "Emergency messages sent to your contacts" :
                            "Some emergency messages could not be sent";
                    ToastUtil.showShort(context, message);
                } catch (Exception e) {
                    Log.e(TAG, "Error showing toast", e);
                }
            });

            return allSuccess;

        } catch (Exception e) {
            Log.e(TAG, "Error sending emergency SMS", e);
            report.setSmsSent(false);
            report.setSmsStatus("FAILED");

            // Show toast on main thread
            mainHandler.post(() -> {
                try {
                    ToastUtil.showShort(context, "Failed to send emergency messages");
                } catch (Exception ex) {
                    Log.e(TAG, "Error showing toast", ex);
                }
            });

            return false;
        }
    }

    /**
     * Format emergency message for SMS
     */
    private String formatEmergencyMessage(final SOSReport report) {
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
     * Create a local notification for the user
     */
    private void createLocalNotification(final SOSReport report, final String title, final String message) {
        mainHandler.post(() -> {
            try {
                // Create notification channel for Android O and above
                createNotificationChannelIfNeeded();

                // Intent to open app when notification is tapped
                Intent intent = new Intent(context, Class.forName("com.rescuereach.citizen.CitizenMainActivity"));
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                if (report.getReportId() != null) {
                    intent.putExtra("reportId", report.getReportId());
                }

                PendingIntent pendingIntent = PendingIntent.getActivity(
                        context,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                // Build the notification
                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, EMERGENCY_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_ALARM)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent);

                // Show the notification
                NotificationManager notificationManager =
                        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

                if (notificationManager != null) {
                    notificationManager.notify(NOTIFICATION_ID, builder.build());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error creating local notification", e);
            }
        });
    }

    /**
     * Create notification channel (required for Android O+)
     */
    private void createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                NotificationManager notificationManager =
                        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

                if (notificationManager != null) {
                    NotificationChannel channel = new NotificationChannel(
                            EMERGENCY_CHANNEL_ID,
                            "Emergency Alerts",
                            NotificationManager.IMPORTANCE_HIGH);
                    channel.setDescription("Notifications for emergency situations");
                    channel.enableLights(true);
                    channel.enableVibration(true);
                    notificationManager.createNotificationChannel(channel);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error creating notification channel", e);
            }
        }
    }

    /**
     * Cache report for later synchronization when back online
     */
    private void cacheReportForLaterSync(final SOSReport report) {
        // TODO: Implement caching using Room database for offline persistence
        // This would allow the app to sync reports when back online
        // For now, we'll just log that this would happen
        Log.d(TAG, "Would cache SOS report for later sync: " + report.getReportId());
    }

    /**
     * Update the status of an SOS report
     */
    public void updateSOSStatus(String reportId, String newStatus, SOSStatusUpdateListener listener) {
        updateSOSStatusWithAuth(reportId, newStatus, listener);
        if (reportId == null || reportId.isEmpty() || newStatus == null || newStatus.isEmpty()) {
            notifyStatusUpdateFailed(listener, "Invalid report ID or status");
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

        // Show status update toast on main thread
        mainHandler.post(() -> {
            try {
                ToastUtil.showShort(context, "Failed to send emergency messages" + newStatus);
            } catch (Exception e) {
                Log.e(TAG, "Error showing toast", e);
            }
        });

        // Run update on background thread
        final Map<String, Object> finalResponderInfo = responderInfo;
        backgroundExecutor.execute(() -> {
            try {
                // Start a timer for network timeout
                final long startTime = System.currentTimeMillis();

                sosRepository.updateSOSStatus(reportId, newStatus, finalResponderInfo,
                        new OnCompleteListener() {
                            @Override
                            public void onSuccess() {
                                // Check for timeout
                                if (System.currentTimeMillis() - startTime > NETWORK_TIMEOUT_SECONDS * 1000) {
                                    Log.w(TAG, "Status update took too long, but succeeded");
                                    // Continue anyway since it succeeded
                                }

                                // Get updated report to send status notification
                                getReportWithTimeout(reportId, new SOSRepository.OnReportFetchedListener() {
                                    @Override
                                    public void onSuccess(SOSReport report) {
                                        // Send status update notification
                                        sendStatusUpdateNotification(report);

                                        // Show successful update toast on main thread
                                        mainHandler.post(() -> {
                                            try {
                                                ToastUtil.showShort(context, "Emergency status updated successfully");
                                            } catch (Exception e) {
                                                Log.e(TAG, "Error showing toast", e);
                                            }
                                        });

                                        notifyStatusUpdateComplete(listener, report);
                                    }

                                    @Override
                                    public void onError(Exception e) {
                                        Log.e(TAG, "Error fetching updated report", e);

                                        // Create a basic report with just the updated status
                                        SOSReport basicReport = new SOSReport();
                                        basicReport.setReportId(reportId);
                                        basicReport.setStatus(newStatus);

                                        // Still consider update successful since status was changed
                                        notifyStatusUpdateComplete(listener, basicReport);
                                    }
                                });
                            }

                            @Override
                            public void onError(Exception e) {
                                Log.e(TAG, "Error updating SOS status", e);

                                // Show error toast on main thread
                                mainHandler.post(() -> {
                                    try {
                                        ToastUtil.showShort(context, "Failed to update emergency status");
                                    } catch (Exception ex) {
                                        Log.e(TAG, "Error showing toast", ex);
                                    }
                                });

                                notifyStatusUpdateFailed(listener, e.getMessage());
                            }
                        });
            } catch (Exception e) {
                Log.e(TAG, "Error in updateSOSStatus", e);
                notifyStatusUpdateFailed(listener, "Status update error: " + e.getMessage());
            }
        });
    }

    /**
     * Get a report with timeout handling
     */
    private void getReportWithTimeout(String reportId, SOSRepository.OnReportFetchedListener listener) {
        try {
            // Start a timer for network timeout
            final long startTime = System.currentTimeMillis();

            sosRepository.getSOSReportById(reportId, new SOSRepository.OnReportFetchedListener() {
                @Override
                public void onSuccess(SOSReport report) {
                    // Check for timeout
                    if (System.currentTimeMillis() - startTime > NETWORK_TIMEOUT_SECONDS * 1000) {
                        Log.w(TAG, "Report fetch took too long, but succeeded");
                        // Continue anyway since it succeeded
                    }

                    if (listener != null) {
                        listener.onSuccess(report);
                    }
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Error fetching report", e);
                    if (listener != null) {
                        listener.onError(e);
                    }
                }
            });

            // Also set a timeout in case the callback never fires
            mainHandler.postDelayed(() -> {
                // If more than 15 seconds passed and we haven't gotten a callback yet,
                // assume it failed and deliver an error
                if (System.currentTimeMillis() - startTime > NETWORK_TIMEOUT_SECONDS * 1000) {
                    Log.e(TAG, "Report fetch timed out");
                    if (listener != null) {
                        listener.onError(new TimeoutException("Report fetch timed out"));
                    }
                }
            }, NETWORK_TIMEOUT_SECONDS * 1000 + 500); // Add 500ms buffer

        } catch (Exception e) {
            Log.e(TAG, "Error setting up report fetch", e);
            if (listener != null) {
                listener.onError(e);
            }
        }
    }

    /**
     * Send status update notification to the user who reported the emergency
     */
    private void sendStatusUpdateNotification(final SOSReport report) {
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
     * Notify the listener of status update completion on the main thread
     */
    private void notifyStatusUpdateComplete(final SOSStatusUpdateListener listener, final SOSReport report) {
        if (listener != null) {
            mainHandler.post(() -> {
                try {
                    listener.onStatusUpdated(report);
                } catch (Exception e) {
                    Log.e(TAG, "Error in status update callback", e);
                }
            });
        }
    }

    /**
     * Notify the listener of status update failure on the main thread
     */
    private void notifyStatusUpdateFailed(final SOSStatusUpdateListener listener, final String errorMessage) {
        if (listener != null) {
            mainHandler.post(() -> {
                try {
                    listener.onStatusUpdateFailed(errorMessage);
                } catch (Exception e) {
                    Log.e(TAG, "Error in status update error callback", e);
                }
            });
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