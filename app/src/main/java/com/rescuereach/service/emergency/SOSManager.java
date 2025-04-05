package com.rescuereach.service.emergency;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.rescuereach.R;
import com.rescuereach.service.auth.UserSessionManager;
import com.rescuereach.util.LocationManager; // Use your existing LocationManager

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages SOS functionality including quick SOS, location sharing during emergency,
 * alerting emergency contacts, and handling emergency messages
 */
public class SOSManager {

    private static final String TAG = "SOSManager";
    private static SOSManager instance;
    private final Context context;
    private final UserSessionManager sessionManager;
    private final LocationManager locationManager;
    private final FirebaseFirestore db;

    private boolean isSendingEmergency = false;

    private SOSManager(Context context) {
        this.context = context.getApplicationContext();
        this.sessionManager = UserSessionManager.getInstance(context);
        this.locationManager = new LocationManager(context); // Create new instance
        this.db = FirebaseFirestore.getInstance();
    }

    public static synchronized SOSManager getInstance(Context context) {
        if (instance == null) {
            instance = new SOSManager(context);
        }
        return instance;
    }

    /**
     * Send SOS alert based on user settings
     */
    public void sendSOSAlert(final OnSOSResultListener listener) {
        if (isSendingEmergency) {
            listener.onError(new Exception("SOS already in progress"));
            return;
        }

        isSendingEmergency = true;

        // Get user details
        String userId = sessionManager.getUserId();
        String userName = sessionManager.getFullName();
        String phoneNumber = sessionManager.getSavedPhoneNumber();

        if (userId == null || userName == null || phoneNumber == null) {
            isSendingEmergency = false;
            listener.onError(new Exception("User details not available"));
            return;
        }

        // Create emergency data
        final String emergencyId = UUID.randomUUID().toString();
        final Date timestamp = new Date();
        final Map<String, Object> emergencyData = new HashMap<>();
        emergencyData.put("emergencyId", emergencyId);
        emergencyData.put("userId", userId);
        emergencyData.put("userName", userName);
        emergencyData.put("phoneNumber", phoneNumber);
        emergencyData.put("timestamp", timestamp);
        emergencyData.put("status", "pending");

        // Check if location sharing is enabled for emergencies
        if (sessionManager.getEmergencyPreference("share_location_emergency", true)) {
            // Get location and include it in the emergency data
            // Use LocationUpdateListener instead of OnLocationResultListener
            locationManager.shareLocationDuringEmergency(new LocationManager.LocationUpdateListener() {
                @Override
                public void onLocationUpdated(Location location) {
                    // Add location to emergency data
                    GeoPoint geoPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
                    emergencyData.put("location", geoPoint);

                    // Save emergency to Firestore and continue the process
                    saveEmergencyAndContinue(emergencyId, emergencyData, listener);
                }

                @Override
                public void onLocationError(String message) {
                    // Continue without location
                    Log.e(TAG, "Location error: " + message);
                    saveEmergencyAndContinue(emergencyId, emergencyData, listener);
                }
            });
        } else {
            // Continue without location
            saveEmergencyAndContinue(emergencyId, emergencyData, listener);
        }
    }

    private void saveEmergencyAndContinue(final String emergencyId,
                                          final Map<String, Object> emergencyData,
                                          final OnSOSResultListener listener) {

        // Save emergency to Firestore
        db.collection("emergencies")
                .document(emergencyId)
                .set(emergencyData)
                .addOnSuccessListener(aVoid -> {
                    // Alert emergency contacts if enabled
                    if (sessionManager.getEmergencyPreference("alert_contacts", true)) {
                        alertEmergencyContacts(emergencyId, emergencyData);
                    }

                    isSendingEmergency = false;
                    listener.onSuccess(emergencyId);
                })
                .addOnFailureListener(e -> {
                    isSendingEmergency = false;
                    listener.onError(e);
                });
    }

    private void alertEmergencyContacts(String emergencyId, Map<String, Object> emergencyData) {
        // Get emergency contact
        String emergencyContact = sessionManager.getEmergencyContactPhone();
        if (emergencyContact == null || emergencyContact.isEmpty()) {
            return;
        }

        // Get message template
        String messageTemplate = sessionManager.getStringPreference("emergency_message_template",
                context.getString(R.string.default_emergency_message));

        // Add location info if available
        String locationInfo = "";
        if (emergencyData.containsKey("location")) {
            GeoPoint location = (GeoPoint) emergencyData.get("location");
            locationInfo = "\nLocation: https://maps.google.com/maps?q=" +
                    location.getLatitude() + "," + location.getLongitude();
        }

        // Format message
        String userName = sessionManager.getFullName();
        String finalMessage = messageTemplate + "\n" +
                "Name: " + userName + locationInfo;

        // TODO: Implement SMS functionality using Twilio in the future
        // For now, just log the message
        Log.d(TAG, "Would send SMS to " + emergencyContact + ": " + finalMessage);

        // Store the message in Firestore for the emergency record
        db.collection("emergencies").document(emergencyId)
                .update("messageToContact", finalMessage)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to save message", e));
    }

    /**
     * Make an emergency call
     */
    public void makeEmergencyCall() {
        // Check if we should make emergency call (always allowed regardless of settings)
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_DIAL);
        intent.setData(android.net.Uri.parse("tel:112")); // Using 112 as a universal emergency number
        intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * Check if quick SOS is enabled
     */
    public boolean isQuickSOSEnabled() {
        return sessionManager.getEmergencyPreference("quick_sos", true);
    }

    /**
     * Check if location sharing during emergency is enabled
     */
    public boolean isLocationSharingEnabledForEmergency() {
        return sessionManager.getEmergencyPreference("share_location_emergency", true);
    }

    /**
     * Check if alert emergency contacts is enabled
     */
    public boolean isAlertContactsEnabled() {
        return sessionManager.getEmergencyPreference("alert_contacts", true);
    }

    public interface OnSOSResultListener {
        void onSuccess(String emergencyId);
        void onError(Exception e);
    }
}