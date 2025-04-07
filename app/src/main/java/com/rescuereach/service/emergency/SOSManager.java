package com.rescuereach.service.emergency;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Build;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.room.Room;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.rescuereach.R;
import com.rescuereach.data.model.SOSReport;
import com.rescuereach.data.room.AppDatabase;
import com.rescuereach.data.room.entity.SOSEntity;
import com.rescuereach.service.auth.UserSessionManager;
import com.rescuereach.util.ConnectivityManager;
import com.rescuereach.util.LocationManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Manages emergency SOS alerts, handling both online and offline scenarios
 */
public class SOSManager {
    private static final String TAG = "SOSManager";

    private static SOSManager instance;

    private final Context context;
    private final UserSessionManager sessionManager;
    private final LocationManager locationManager;
    private final ConnectivityManager connectivityManager;
    private final FirebaseFirestore firestore;
    private final AppDatabase roomDatabase;
    private final Executor backgroundExecutor;

    /**
     * Interface for SOS operation callbacks
     */
    public interface SOSListener {
        void onSOSInitiated();
        void onSOSSuccess(String sosId);
        void onSOSFailure(String errorMessage);
    }

    private SOSManager(Context context) {
        this.context = context.getApplicationContext();
        this.sessionManager = UserSessionManager.getInstance(context);
        this.locationManager = new LocationManager(context);
        this.connectivityManager = new ConnectivityManager(context);
        this.firestore = FirebaseFirestore.getInstance();

        // Initialize Room database
        this.roomDatabase = Room.databaseBuilder(
                        context,
                        AppDatabase.class,
                        "rescuereach-db")
                .fallbackToDestructiveMigration()
                .build();

        this.backgroundExecutor = Executors.newSingleThreadExecutor();
    }

    /**
     * Get singleton instance of SOSManager
     */
    public static synchronized SOSManager getInstance(Context context) {
        if (instance == null) {
            instance = new SOSManager(context);
        }
        return instance;
    }

    /**
     * Initiate an emergency SOS alert
     * @param category - The emergency category (police, fire, medical)
     * @param listener - Callback to handle results
     */
    public void initiateEmergencySOS(final String category, final SOSListener listener) {
        // Notify that SOS has been initiated
        if (listener != null) {
            listener.onSOSInitiated();
        }

        // First, get the current location
        Location currentLocation = locationManager.getLastKnownLocation();
        if (currentLocation == null) {
            if (listener != null) {
                listener.onSOSFailure("Unable to determine your location");
            }
            return;
        }

        // Get user info from session
        String userId = sessionManager.getSavedPhoneNumber();
        String userName = sessionManager.getFullName();

        if (userId == null || userId.isEmpty()) {
            if (listener != null) {
                listener.onSOSFailure("User not logged in");
            }
            return;
        }

        // Create GeoPoint for Firestore
        GeoPoint locationPoint = new GeoPoint(
                currentLocation.getLatitude(),
                currentLocation.getLongitude());

        // Get device info
        Map<String, Object> deviceInfo = getDeviceInfo();
        int batteryLevel = getBatteryLevel();

        // Get address from location
        String address = "Unknown address"; // Default value
        try {
            address = locationManager.getAddressFromLocation(currentLocation);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get address", e);
        }

        // Create SOS report
        final SOSReport sosReport = new SOSReport(
                userId,
                userName,
                category,
                locationPoint,
                address,
                deviceInfo,
                batteryLevel);

        // Check if we're online or offline
        if (connectivityManager.isNetworkAvailable()) {
            // We're online, send directly to Firestore
            sendOnlineSOS(sosReport, listener);
        } else {
            // We're offline, save locally and try SMS if possible
            sendOfflineSOS(sosReport, listener);
        }
    }

    /**
     * Send SOS report when online
     */
    private void sendOnlineSOS(final SOSReport sosReport, final SOSListener listener) {
        // Save to Firestore
        firestore.collection("sos")
                .document(sosReport.getId())
                .set(sosReport.toMap())
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            // Update Realtime DB (would be implemented with a separate method)
                            updateRealtimeDB(sosReport);

                            // Send SMS via Twilio if configured
                            sendTwilioSMS(sosReport);

                            // Notify success
                            if (listener != null) {
                                listener.onSOSSuccess(sosReport.getId());
                            }
                        } else {
                            Log.e(TAG, "Error saving SOS report to Firestore", task.getException());

                            // Try offline as fallback
                            sendOfflineSOS(sosReport, listener);
                        }
                    }
                });
    }

    /**
     * Process SOS report when offline
     */
    private void sendOfflineSOS(final SOSReport sosReport, final SOSListener listener) {
        // Run in background
        backgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // Save to Room database first
                    SOSEntity sosEntity = convertToEntity(sosReport);
                    roomDatabase.sosDao().insert(sosEntity);

                    // Try to send SMS directly if we have permission
                    boolean smsSent = false;
                    if (hasSmsPermission()) {
                        smsSent = sendDirectSMS(sosReport);
                    }

                    // Queue for sync when network returns
                    // This would be implemented with WorkManager in a full solution
                    // queueForSync(sosReport.getId());

                    // Update SMS status in local database
                    if (smsSent) {
                        sosEntity.setSmsStatus(SOSReport.SMS_STATUS_SENT);
                        roomDatabase.sosDao().update(sosEntity);
                    }

                    // Notify on main thread
                    if (listener != null) {
                        listener.onSOSSuccess(sosReport.getId());
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Error saving offline SOS", e);

                    // Notify failure on main thread
                    if (listener != null) {
                        listener.onSOSFailure("Failed to save emergency alert: " + e.getMessage());
                    }
                }
            }
        });
    }

    /**
     * Send SMS via Twilio API (placeholder - would be implemented with your Twilio credentials)
     */
    private void sendTwilioSMS(final SOSReport sosReport) {
        // This would be implemented with Twilio API
        // For now, we'll just mark as sent in Firestore

        final DocumentReference sosRef = firestore.collection("sos").document(sosReport.getId());

        sosRef.update("smsStatus", SOSReport.SMS_STATUS_SENT)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update SMS status", e));

        // In a real implementation, you would:
        // 1. Call Twilio API
        // 2. Handle the response
        // 3. Update status based on Twilio callback
    }

    /**
     * Send direct SMS using the device's SMS manager
     * @return true if SMS was sent successfully
     */
    private boolean sendDirectSMS(final SOSReport sosReport) {
        try {
            // Get emergency contact(s) from session manager
            String emergencyContact = sessionManager.getEmergencyContact();

            if (emergencyContact == null || emergencyContact.isEmpty()) {
                Log.w(TAG, "No emergency contact available for direct SMS");
                return false;
            }

            // Create SOS message
            String message = String.format(
                    context.getString(R.string.sos_sms_template),
                    sosReport.getUserName(),
                    sosReport.getCategory().toUpperCase(),
                    sosReport.getAddress(),
                    sosReport.getLocation().getLatitude(),
                    sosReport.getLocation().getLongitude());

            // Send SMS
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(
                    emergencyContact,
                    null,
                    message,
                    null,
                    null);

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error sending direct SMS", e);
            return false;
        }
    }

    /**
     * Update Realtime Database with minimal SOS data
     */
    private void updateRealtimeDB(final SOSReport sosReport) {
        // This would be implemented with Firebase Realtime Database
        // For a complete implementation, you would:
        // 1. Connect to the Realtime DB
        // 2. Add minimal data needed for real-time tracking
        // 3. Set up listeners for status changes
    }

    /**
     * Check if app has SMS permission
     */
    private boolean hasSmsPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Get device information for the SOS report
     */
    private Map<String, Object> getDeviceInfo() {
        Map<String, Object> deviceInfo = new HashMap<>();
        deviceInfo.put("manufacturer", Build.MANUFACTURER);
        deviceInfo.put("model", Build.MODEL);
        deviceInfo.put("osVersion", Build.VERSION.RELEASE);
        deviceInfo.put("sdkVersion", Build.VERSION.SDK_INT);
        return deviceInfo;
    }

    /**
     * Get current battery level as percentage
     */
    private int getBatteryLevel() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, filter);

        if (batteryStatus == null) {
            return -1;
        }

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        if (level == -1 || scale == -1) {
            return -1;
        }

        return (int) ((level / (float) scale) * 100);
    }

    /**
     * Convert SOSReport to Room entity
     */
    private SOSEntity convertToEntity(SOSReport report) {
        SOSEntity entity = new SOSEntity();
        entity.setId(report.getId());
        entity.setUserId(report.getUserId());
        entity.setCategory(report.getCategory());
        entity.setLatitude(report.getLocation().getLatitude());
        entity.setLongitude(report.getLocation().getLongitude());
        entity.setAddress(report.getAddress());
        entity.setStatus(report.getStatus());
        entity.setSmsStatus(report.getSmsStatus());
        entity.setCreatedAt(report.getCreatedAt().getTime());
        entity.setNeedsSync(true);
        return entity;
    }
}