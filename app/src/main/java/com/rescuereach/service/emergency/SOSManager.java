package com.rescuereach.service.emergency;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import com.rescuereach.service.communication.TwilioManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.rescuereach.data.model.SOSReport;
import com.rescuereach.service.auth.UserSessionManager;
import com.rescuereach.util.LocationManager;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class SOSManager {
    private static final String TAG = "SOSManager";
    private static SOSManager instance;

    private final Context context;
    private final FirebaseFirestore db;
    private final DatabaseReference realtimeDb;
    private final UserSessionManager sessionManager;
    private final LocationManager locationManager;

    // Interfaces for callbacks
    public interface SOSListener {
        void onSOSInitiated();
        void onSOSSuccess();
        void onSOSFailure(String errorMessage);
    }

    private SOSManager(Context context) {
        this.context = context.getApplicationContext();
        this.db = FirebaseFirestore.getInstance();
        this.realtimeDb = FirebaseDatabase.getInstance().getReference();
        this.sessionManager = UserSessionManager.getInstance(context);
        this.locationManager = new LocationManager(context);
    }

    public static synchronized SOSManager getInstance(Context context) {
        if (instance == null) {
            instance = new SOSManager(context);
        }
        return instance;
    }

    /**
     * Initiates an emergency SOS request
     *
     * @param category Category of emergency (police, fire, medical)
     * @param listener Callback for SOS status updates
     */
    public void initiateEmergencySOS(String category, SOSListener listener) {
        listener.onSOSInitiated();

        // Get high-priority location for emergency
        locationManager.shareLocationDuringEmergency(new LocationManager.LocationUpdateListener() {
            @Override
            public void onLocationUpdated(Location location) {
                processSOSWithLocation(category, location, listener);
            }

            @Override
            public void onLocationError(String message) {
                listener.onSOSFailure("Unable to get your location: " + message);
            }
        });
    }

    private void processSOSWithLocation(String category, Location location, SOSListener listener) {
        // Get user information from session
        String userId = sessionManager.getUserId();
        String phoneNumber = sessionManager.getSavedPhoneNumber();
        String emergencyContact = sessionManager.getEmergencyContactPhone();

        if (userId == null || phoneNumber == null) {
            listener.onSOSFailure("User account information missing");
            return;
        }

        // Get address from location if possible
        String address = getAddressFromLocation(location);

        // Create SOS report object
        SOSReport sosReport = new SOSReport(
                userId,
                phoneNumber,
                emergencyContact,
                category,
                location.getLatitude(),
                location.getLongitude(),
                Timestamp.now(),
                getDeviceInfo(),
                !isNetworkAvailable(),
                address
        );

        // Handle based on network availability
        if (isNetworkAvailable()) {
            submitSOSToFirebase(sosReport, listener);
        } else {
            // Store locally for later sync
            queueOfflineSOS(sosReport, listener);

            // Attempt to send SMS in offline mode
            // (This will be implemented in the next steps)
            sendEmergencySMS(sosReport);
        }
    }

    private String getAddressFromLocation(Location location) {
        try {
            Geocoder geocoder = new Geocoder(context, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(
                    location.getLatitude(), location.getLongitude(), 1);

            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                StringBuilder sb = new StringBuilder();

                // Get address line
                String addressLine = address.getAddressLine(0);
                if (addressLine != null) {
                    return addressLine;
                }

                // Or build from components
                for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                    if (address.getAddressLine(i) != null) {
                        sb.append(address.getAddressLine(i));
                        if (i < address.getMaxAddressLineIndex()) {
                            sb.append(", ");
                        }
                    }
                }

                if (sb.length() > 0) {
                    return sb.toString();
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error getting address", e);
        }

        return "Unknown location";
    }

    private void submitSOSToFirebase(SOSReport sosReport, SOSListener listener) {
        // Add to Firestore
        db.collection("incidents")
                .add(sosReport.toMap())
                .addOnSuccessListener(documentReference -> {
                    String sosId = documentReference.getId();
                    sosReport.setId(sosId);

                    // Add to realtime database for immediate alerts
                    addSOSToRealtimeDatabase(sosReport, listener);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error adding SOS report", e);
                    listener.onSOSFailure("Failed to submit emergency: " + e.getMessage());
                });
    }

    private void addSOSToRealtimeDatabase(SOSReport sosReport, SOSListener listener) {
        // Add to /alerts node for real-time monitoring
        realtimeDb.child("alerts")
                .child(sosReport.getId())
                .setValue(sosReport.toMap())
                .addOnSuccessListener(aVoid -> {
                    // Success!
                    listener.onSOSSuccess();

                    // Send SMS notification to emergency contact
                    if (sosReport.getEmergencyContact() != null && !sosReport.getEmergencyContact().isEmpty()) {
                        sendEmergencySMS(sosReport);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error adding alert to Realtime DB", e);
                    // Still consider this a success as the main data is in Firestore
                    listener.onSOSSuccess();
                });
    }

    private void queueOfflineSOS(SOSReport sosReport, SOSListener listener) {
        // For now, just log that we would store this locally
        Log.d(TAG, "Network unavailable - SOS would be queued locally");

        // In next steps, we would implement Room database storage

        // For now, consider this "successful" for the demo
        listener.onSOSSuccess();
    }

    private void sendEmergencySMS(SOSReport sosReport) {
        // Skip if no emergency contact
        if (sosReport.getEmergencyContact() == null || sosReport.getEmergencyContact().isEmpty()) {
            Log.d(TAG, "No emergency contact set, skipping SMS notification");
            return;
        }

        // Get user's name
        String userName = sessionManager.getFullName();

        // Get Twilio manager instance
        TwilioManager twilioManager = TwilioManager.getInstance(context);

        // Send SMS with delivery tracking
        twilioManager.sendEmergencySMS(sosReport, userName, new TwilioManager.SMSDeliveryListener() {
            @Override
            public void onSMSSent(String recipientNumber) {
                Log.d(TAG, "Emergency SMS sent to " + recipientNumber);
                // Update Firestore with SMS status if online
                if (isNetworkAvailable()) {
                    updateSOSReportField(sosReport.getId(), "smsStatus", "sent");
                }
            }

            @Override
            public void onSMSDelivered(String recipientNumber) {
                Log.d(TAG, "Emergency SMS delivered to " + recipientNumber);
                // Update Firestore with SMS delivery status if online
                if (isNetworkAvailable()) {
                    updateSOSReportField(sosReport.getId(), "smsStatus", "delivered");
                }
            }

            @Override
            public void onSMSFailed(String recipientNumber, String errorMessage) {
                Log.e(TAG, "Failed to send emergency SMS to " + recipientNumber + ": " + errorMessage);
                // Update Firestore with SMS failure status if online
                if (isNetworkAvailable()) {
                    updateSOSReportField(sosReport.getId(), "smsStatus", "failed");
                    updateSOSReportField(sosReport.getId(), "smsError", errorMessage);
                }
            }
        });
    }

    private void updateSOSReportField(String reportId, String field, String value) {
        if (reportId == null || reportId.isEmpty()) return;

        db.collection("incidents").document(reportId)
                .update(field, value)
                .addOnFailureListener(e ->
                        Log.e(TAG, "Failed to update SOS report field: " + field, e));
    }

    private String createEmergencyMessageBody(SOSReport sosReport) {
        StringBuilder sb = new StringBuilder();
        sb.append("EMERGENCY ALERT from RescueReach: ");

        // Add emergency type
        switch (sosReport.getCategory()) {
            case SOSReport.CATEGORY_POLICE:
                sb.append("POLICE emergency reported. ");
                break;
            case SOSReport.CATEGORY_FIRE:
                sb.append("FIRE emergency reported. ");
                break;
            case SOSReport.CATEGORY_MEDICAL:
                sb.append("MEDICAL emergency reported. ");
                break;
            default:
                sb.append("Emergency reported. ");
        }

        // Add location info
        sb.append("Location: ");
        if (sosReport.getAddress() != null && !sosReport.getAddress().equals("Unknown location")) {
            sb.append(sosReport.getAddress());
        } else {
            sb.append("GPS: ").append(sosReport.getLatitude()).append(", ").append(sosReport.getLongitude());
        }

        return sb.toString();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    private String getDeviceInfo() {
        return "Android " + Build.VERSION.RELEASE + " (" + Build.MODEL + ")";
    }
}