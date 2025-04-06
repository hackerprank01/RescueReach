package com.rescuereach.service.communication;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;
import com.rescuereach.data.model.SOSReport;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Manages SMS communication via Twilio API through Firebase Cloud Functions
 */
public class TwilioManager {
    private static final String TAG = "TwilioManager";
    private static TwilioManager instance;

    private final Context context;
    private final FirebaseFunctions functions;
    private boolean isDemoMode = true; // Set to false when Firebase Functions are ready

    // SMS templates for different emergency types
    private static final String TEMPLATE_POLICE =
            "EMERGENCY ALERT: %s needs help! POLICE emergency reported at: %s. " +
                    "Current location: %s. Please contact emergency services or check RescueReach for status.";

    private static final String TEMPLATE_FIRE =
            "EMERGENCY ALERT: %s needs help! FIRE emergency reported at: %s. " +
                    "Current location: %s. Please contact emergency services or check RescueReach for status.";

    private static final String TEMPLATE_MEDICAL =
            "EMERGENCY ALERT: %s needs help! MEDICAL emergency reported at: %s. " +
                    "Current location: %s. Please contact emergency services or check RescueReach for status.";

    private static final String TEMPLATE_GENERAL =
            "EMERGENCY ALERT: %s needs help! Emergency reported at: %s. " +
                    "Current location: %s. Please contact emergency services or check RescueReach for status.";

    // Interface for SMS delivery callbacks
    public interface SMSDeliveryListener {
        void onSMSSent(String recipientNumber);
        void onSMSDelivered(String recipientNumber);
        void onSMSFailed(String recipientNumber, String errorMessage);
    }

    private TwilioManager(Context context) {
        this.context = context.getApplicationContext();
        this.functions = FirebaseFunctions.getInstance();
    }

    public static synchronized TwilioManager getInstance(Context context) {
        if (instance == null) {
            instance = new TwilioManager(context);
        }
        return instance;
    }

    /**
     * Sends an emergency SMS based on the SOS report
     *
     * @param sosReport The SOS report containing emergency details
     * @param listener Callback to track SMS delivery status
     */
    public void sendEmergencySMS(SOSReport sosReport, String senderName, SMSDeliveryListener listener) {
        // Verify recipient number
        String recipientNumber = sosReport.getEmergencyContact();
        if (recipientNumber == null || recipientNumber.isEmpty()) {
            Log.e(TAG, "Cannot send SMS: No emergency contact provided");
            if (listener != null) {
                listener.onSMSFailed("unknown", "No emergency contact provided");
            }
            return;
        }

        // Clean phone number (ensure it has international format)
        recipientNumber = formatPhoneNumber(recipientNumber);

        // Prepare message
        String messageBody = createMessageFromTemplate(sosReport, senderName);

        // Check if we're in demo mode
        if (isDemoMode) {
            // Simulate sending SMS (for testing only)
            Log.d(TAG, "DEMO MODE: Would send SMS to " + recipientNumber + ": " + messageBody);

            // Simulate delay and success
            simulateSMSDelivery(recipientNumber, listener);
            return;
        }

        // Prepare data for Firebase Function
        Map<String, Object> data = new HashMap<>();
        data.put("to", recipientNumber);
        data.put("body", messageBody);
        data.put("sosId", sosReport.getId());

        // Call Firebase Function
        functions.getHttpsCallable("sendEmergencySMS")
                .call(data)
                .addOnSuccessListener(result -> {
                    // Parse result
                    if (result != null) {
                        Map<String, Object> resultData = (Map<String, Object>) result.getData();
                        boolean success = (boolean) resultData. getOrDefault("success", false);
                        String sid = (String) resultData.getOrDefault("sid", "");

                        if (success) {
                            Log.d(TAG, "SMS sent successfully, SID: " + sid);
                            if (listener != null) {
                                listener.onSMSSent(recipientNumber);
                                // Note: actual delivery confirmation would come via webhook to Firebase
                                // For now, assume sent = delivered
                                listener.onSMSDelivered(recipientNumber);
                            }
                        } else {
                            String error = (String) resultData.getOrDefault("error", "Unknown error");
                            Log.e(TAG, "Failed to send SMS: " + error);
                            if (listener != null) {
                                listener.onSMSFailed(recipientNumber, error);
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error calling Firebase Function", e);
                    if (listener != null) {
                        listener.onSMSFailed(recipientNumber, e.getMessage());
                    }
                });
    }

    /**
     * Creates a specific message based on the emergency type
     */
    private String createMessageFromTemplate(SOSReport sosReport, String senderName) {
        // Use default name if none provided
        if (senderName == null || senderName.isEmpty()) {
            senderName = "A RescueReach user";
        }

        // Format timestamp
        String timestamp = formatTimestamp(sosReport.getTimestamp().toDate().getTime());

        // Format location
        String location = sosReport.getAddress();
        if (location == null || location.equals("Unknown location")) {
            location = "GPS: " + sosReport.getLatitude() + ", " + sosReport.getLongitude();
        }

        // Select template based on emergency type
        String template;
        switch (sosReport.getCategory()) {
            case SOSReport.CATEGORY_POLICE:
                template = TEMPLATE_POLICE;
                break;
            case SOSReport.CATEGORY_FIRE:
                template = TEMPLATE_FIRE;
                break;
            case SOSReport.CATEGORY_MEDICAL:
                template = TEMPLATE_MEDICAL;
                break;
            default:
                template = TEMPLATE_GENERAL;
        }

        // Format message with details
        return String.format(template, senderName, timestamp, location);
    }

    /**
     * Simulates SMS delivery for demo/testing purposes
     */
    private void simulateSMSDelivery(String recipientNumber, SMSDeliveryListener listener) {
        if (listener != null) {
            // Simulate SMS sent after 1 second
            new android.os.Handler().postDelayed(() -> {
                listener.onSMSSent(recipientNumber);

                // Simulate SMS delivered after another 2 seconds
                new android.os.Handler().postDelayed(() -> {
                    listener.onSMSDelivered(recipientNumber);
                }, 2000);
            }, 1000);
        }
    }

    /**
     * Formats phone number to ensure it has the correct international format
     */
    private String formatPhoneNumber(String phoneNumber) {
        // Remove any non-digit characters
        String digitsOnly = phoneNumber.replaceAll("\\D", "");

        // Ensure it has country code (default to India +91)
        if (digitsOnly.length() == 10) {
            return "+91" + digitsOnly;
        } else if (digitsOnly.startsWith("91") && digitsOnly.length() == 12) {
            return "+" + digitsOnly;
        } else if (!digitsOnly.startsWith("+")) {
            return "+" + digitsOnly;
        }

        return phoneNumber;
    }

    /**
     * Formats timestamp for SMS
     */
    private String formatTimestamp(long timestamp) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(timestamp));
    }
}