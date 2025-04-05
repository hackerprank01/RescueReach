package com.rescuereach.service.emergency;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.rescuereach.R;
import com.rescuereach.service.auth.UserSessionManager;
import com.rescuereach.util.LocationManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages sending emergency alerts to emergency contacts
 * based on user privacy settings
 */
public class EmergencyAlertManager {
    private static final String TAG = "EmergencyAlertManager";

    private final Context context;
    private final UserSessionManager sessionManager;
    private final FirebaseFirestore db;

    public interface AlertSentListener {
        void onAlertSent(String alertId);
        void onError(Exception e);
    }

    public EmergencyAlertManager(Context context) {
        this.context = context.getApplicationContext();
        this.sessionManager = UserSessionManager.getInstance(context);
        this.db = FirebaseFirestore.getInstance();
    }

    /**
     * Send emergency alert to emergency contact
     */
    public void sendEmergencyAlert(AlertSentListener listener) {
        // Check if the user has emergency contact
        String emergencyContact = sessionManager.getEmergencyContactPhone();
        if (emergencyContact == null || emergencyContact.isEmpty()) {
            if (listener != null) {
                listener.onError(new Exception("No emergency contact set"));
            }
            return;
        }

        // Generate unique ID for this alert
        final String alertId = UUID.randomUUID().toString();

        // Prepare alert data
        final Map<String, Object> alertData = new HashMap<>();
        alertData.put("alertId", alertId);
        alertData.put("userId", sessionManager.getUserId());
        alertData.put("userName", sessionManager.getFullName());
        alertData.put("timestamp", System.currentTimeMillis());
        alertData.put("emergencyContact", emergencyContact);

        // Check if we should include location
        boolean shareLocationInEmergency = sessionManager.getEmergencyPreference("share_location_emergency", true);

        if (shareLocationInEmergency) {
            // Get current location
            LocationManager locationManager = new LocationManager(context);
            locationManager.shareLocationDuringEmergency(new LocationManager.LocationUpdateListener() {
                @Override
                public void onLocationUpdated(Location location) {
                    // Add location to alert data
                    GeoPoint geoPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
                    alertData.put("location", geoPoint);

                    // Continue with sending the alert
                    saveAlertAndSendMessage(alertId, alertData, listener);
                }

                @Override
                public void onLocationError(String message) {
                    // Continue without location
                    Log.e(TAG, "Failed to get location for emergency alert: " + message);
                    saveAlertAndSendMessage(alertId, alertData, listener);
                }
            });
        } else {
            // Continue without location
            saveAlertAndSendMessage(alertId, alertData, listener);
        }
    }

    private void saveAlertAndSendMessage(final String alertId,
                                         final Map<String, Object> alertData,
                                         final AlertSentListener listener) {
        // Save alert to Firestore
        db.collection("emergency_alerts")
                .document(alertId)
                .set(alertData)
                .addOnSuccessListener(aVoid -> {
                    // Send SMS to emergency contact
                    sendSMSToEmergencyContact(alertData);

                    // Notify listener
                    if (listener != null) {
                        listener.onAlertSent(alertId);
                    }
                })
                .addOnFailureListener(e -> {
                    if (listener != null) {
                        listener.onError(e);
                    }
                });
    }

    private void sendSMSToEmergencyContact(Map<String, Object> alertData) {
        // Get emergency contact
        String emergencyContact = (String) alertData.get("emergencyContact");

        // Get message template
        String messageTemplate = sessionManager.getStringPreference(
                "emergency_message_template",
                context.getString(R.string.default_emergency_message));

        // Format message with user details
        String userName = sessionManager.getFullName();

        // Add location info if available
        String locationInfo = "";
        if (alertData.containsKey("location")) {
            GeoPoint location = (GeoPoint) alertData.get("location");
            locationInfo = "\nLocation: https://maps.google.com/maps?q=" +
                    location.getLatitude() + "," + location.getLongitude();
        }

        // Format final message
        String finalMessage = messageTemplate + "\n" +
                "Name: " + userName + locationInfo;

        // In a real app, you would integrate with SMS service like Twilio
        // For now, just log the message that would be sent
        Log.d(TAG, "Would send SMS to " + emergencyContact + ": " + finalMessage);

        // TODO: Implement actual SMS sending in production
    }
}