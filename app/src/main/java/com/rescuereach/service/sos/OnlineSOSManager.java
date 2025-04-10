package com.rescuereach.service.sos;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.rescuereach.data.model.EmergencyContact;
import com.rescuereach.data.model.EmergencyService;
import com.rescuereach.data.model.SOSReport;
import com.rescuereach.service.auth.UserSessionManager;
import com.rescuereach.util.NetworkUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages online SOS reporting including Firestore storage, Realtime Database updates,
 * FCM notifications, and Twilio SMS integration
 */
public class OnlineSOSManager {
    private static final String TAG = "OnlineSOSManager";

    // Firebase collections and paths
    private static final String COLLECTION_SOS_REPORTS = "sos_reports";
    private static final String COLLECTION_USERS = "users";
    private static final String REALTIME_DB_SOS_PATH = "active_sos";

    // Context and services
    private final Context context;
    private final UserSessionManager sessionManager;
    private final FirebaseFirestore firestore;
    private final FirebaseDatabase realtimeDb;
    private final FirebaseFunctions functions;

    // SOS report being processed
    private SOSReport sosReport;

    // Listener for SOS processing events
    private SOSProcessingListener processingListener;

    public OnlineSOSManager(Context context) {
        this.context = context.getApplicationContext();
        this.sessionManager = UserSessionManager.getInstance(context);
        this.firestore = FirebaseFirestore.getInstance();
        this.realtimeDb = FirebaseDatabase.getInstance();
        this.functions = FirebaseFunctions.getInstance();
    }

    /**
     * Process an SOS report, including saving to Firestore, updating Realtime DB,
     * sending FCM notifications, and sending SMS via Twilio
     *
     * @param report The SOS report to process
     * @param listener Callback for processing events
     */
    public void processSOS(SOSReport report, SOSProcessingListener listener) {
        Log.d(TAG, "Processing SOS report: " + report.getEmergencyType());

        this.sosReport = report;
        this.processingListener = listener;

        // Check network connectivity
        if (!NetworkUtils.isNetworkAvailable(context)) {
            Log.e(TAG, "Network unavailable, cannot process online");
            if (listener != null) {
                listener.onSOSProcessingFailed(new Exception("Network unavailable"), report);
            }
            return;
        }

        // Mark as online
        sosReport.setOnline(true);

        // First, save to Firestore
        saveToFirestore();
    }

    /**
     * Step 1: Save SOS report to Firestore
     */
    private void saveToFirestore() {
        Log.d(TAG, "Saving SOS report to Firestore: " + sosReport.getReportId());

        // Set status and timestamp
        sosReport.setStatus("PENDING");
        sosReport.setTimestamp(new Date());

        // Save to Firestore with the report ID as the document ID
        DocumentReference docRef = firestore.collection(COLLECTION_SOS_REPORTS)
                .document(sosReport.getReportId());

        docRef.set(sosReport)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "SOS report saved to Firestore successfully");

                    // Update the report document with a server timestamp
                    Map<String, Object> timestampUpdate = new HashMap<>();
                    timestampUpdate.put("serverTimestamp", com.google.firebase.Timestamp.now());

                    docRef.set(timestampUpdate, SetOptions.merge())
                            .addOnSuccessListener(innerVoid -> {
                                // Proceed to update Realtime Database
                                updateRealtimeDatabase();
                            })
                            .addOnFailureListener(e -> {
                                // Not critical if timestamp update fails, still proceed
                                Log.w(TAG, "Error updating server timestamp", e);
                                updateRealtimeDatabase();
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving SOS report to Firestore", e);

                    if (processingListener != null) {
                        processingListener.onSOSProcessingFailed(e, sosReport);
                    }
                });
    }

    /**
     * Step 2: Update Realtime Database for live tracking
     */
    private void updateRealtimeDatabase() {
        Log.d(TAG, "Updating Realtime Database with SOS info");

        // Create a simplified version of the SOS report for Realtime DB
        Map<String, Object> realtimeSosData = createRealtimeDatabaseSosData();

        // Save to active_sos/{reportId}
        DatabaseReference sosRef = realtimeDb.getReference(REALTIME_DB_SOS_PATH)
                .child(sosReport.getReportId());

        sosRef.setValue(realtimeSosData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "SOS data saved to Realtime Database successfully");
                    // Proceed to notify responders via Cloud Functions and FCM
                    notifyResponders();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving SOS data to Realtime Database", e);
                    // Continue with notification even if Realtime DB update fails
                    notifyResponders();
                });
    }

    /**
     * Create a simplified version of the SOS report for Realtime Database
     * This is optimized for live status tracking
     */
    private Map<String, Object> createRealtimeDatabaseSosData() {
        Map<String, Object> data = new HashMap<>();

        // Basic info
        data.put("reportId", sosReport.getReportId());
        data.put("userId", sosReport.getUserId());
        data.put("userPhoneNumber", sosReport.getUserPhoneNumber());
        data.put("userFullName", sosReport.getUserFullName());
        data.put("emergencyType", sosReport.getEmergencyType());
        data.put("status", sosReport.getStatus());
        data.put("timestamp", sosReport.getTimestamp().getTime());

        // Location info
        if (sosReport.getLocation() != null) {
            Map<String, Object> locationData = new HashMap<>();
            locationData.put("latitude", sosReport.getLatitude());
            locationData.put("longitude", sosReport.getLongitude());
            locationData.put("accuracy", sosReport.getLocationAccuracy());
            data.put("location", locationData);
        }

        // Address info
        if (sosReport.getAddress() != null) {
            Map<String, Object> addressData = new HashMap<>();
            addressData.put("address", sosReport.getAddress());
            addressData.put("city", sosReport.getCity());
            addressData.put("state", sosReport.getState());
            data.put("address", addressData);
        }

        // Nearest emergency service
        EmergencyService primaryService = sosReport.getPrimaryEmergencyService();
        if (primaryService != null) {
            Map<String, Object> serviceData = new HashMap<>();
            serviceData.put("name", primaryService.getName());
            serviceData.put("phoneNumber", primaryService.getPhoneNumber());
            serviceData.put("tollFreeNumber", primaryService.getTollFreeNumber());
            serviceData.put("distance", primaryService.getDistance());
            data.put("nearestService", serviceData);
        }

        return data;
    }

    /**
     * Step 3: Notify responders via Cloud Functions and FCM
     */
    private void notifyResponders() {
        Log.d(TAG, "Notifying responders via Cloud Functions");

        // Prepare data for the Cloud Function
        Map<String, Object> data = new HashMap<>();
        data.put("reportId", sosReport.getReportId());
        data.put("emergencyType", sosReport.getEmergencyType());
        data.put("latitude", sosReport.getLatitude());
        data.put("longitude", sosReport.getLongitude());
        data.put("address", sosReport.getAddress());
        data.put("city", sosReport.getCity());
        data.put("state", sosReport.getState());

        // Call the Cloud Function to notify responders
        functions.getHttpsCallable("notifyEmergencyResponders")
                .call(data)
                .addOnSuccessListener(new OnSuccessListener<HttpsCallableResult>() {
                    @Override
                    public void onSuccess(HttpsCallableResult result) {
                        Log.d(TAG, "Responders notified successfully");

                        // Extract response data
                        Map<String, Object> responseData = (Map<String, Object>) result.getData();
                        int notifiedCount = responseData != null && responseData.containsKey("notifiedCount") ?
                                ((Long) responseData.get("notifiedCount")).intValue() : 0;

                        Log.d(TAG, "Notified " + notifiedCount + " responders");

                        // Proceed to send SMS via Twilio
                        sendEmergencyContactSMS();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "Error notifying responders", e);

                        // Continue with SMS even if FCM notification fails
                        sendEmergencyContactSMS();
                    }
                });
    }

    /**
     * Step 4: Send SMS to emergency contacts via Twilio
     */
    private void sendEmergencyContactSMS() {
        Log.d(TAG, "Sending SMS to emergency contacts");

        List<EmergencyContact> contacts = sosReport.getEmergencyContacts();

        // Skip if no emergency contacts
        if (contacts == null || contacts.isEmpty()) {
            Log.w(TAG, "No emergency contacts to notify");
            updateSOSStatus("RECEIVED");
            return;
        }

        // Collect phone numbers
        List<String> phoneNumbers = new ArrayList<>();
        for (EmergencyContact contact : contacts) {
            if (contact.getPhoneNumber() != null && !contact.getPhoneNumber().isEmpty()) {
                phoneNumbers.add(contact.getFormattedPhoneNumber());
            }
        }

        // Skip if no valid phone numbers
        if (phoneNumbers.isEmpty()) {
            Log.w(TAG, "No valid emergency contact phone numbers");
            updateSOSStatus("RECEIVED");
            return;
        }

        // Prepare data for Cloud Function to send SMS
        Map<String, Object> smsData = new HashMap<>();
        smsData.put("reportId", sosReport.getReportId());
        smsData.put("phoneNumbers", phoneNumbers);
        smsData.put("emergencyType", sosReport.getEmergencyType());
        smsData.put("userName", sosReport.getUserFullName());
        smsData.put("address", sosReport.getAddress() != null ? sosReport.getAddress() : "Unknown location");
        smsData.put("city", sosReport.getCity() != null ? sosReport.getCity() : "");
        smsData.put("state", sosReport.getState() != null ? sosReport.getState() : "");

        // Get nearest emergency service info if available
        EmergencyService primaryService = sosReport.getPrimaryEmergencyService();
        if (primaryService != null) {
            smsData.put("nearestServiceName", primaryService.getName());
            smsData.put("nearestServicePhone", primaryService.getEmergencyNumber());
            smsData.put("nearestServiceDistance", primaryService.getFormattedDistance());
        }

        // Call the Cloud Function to send SMS
        functions.getHttpsCallable("sendEmergencySMS")
                .call(smsData)
                .addOnSuccessListener(new OnSuccessListener<HttpsCallableResult>() {
                    @Override
                    public void onSuccess(HttpsCallableResult result) {
                        Log.d(TAG, "Emergency SMS sent successfully via Twilio");

                        // Extract response data
                        Map<String, Object> responseData = (Map<String, Object>) result.getData();
                        boolean success = responseData != null && responseData.containsKey("success") ?
                                (Boolean) responseData.get("success") : false;

                        // Update SMS status in the SOS report
                        updateSMSStatus(success);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "Error sending SMS via Twilio", e);

                        // Update SMS status as failed
                        updateSMSStatus(false);
                    }
                });
    }

    /**
     * Step 5: Update SMS status in the SOS report
     */
    private void updateSMSStatus(boolean success) {
        Log.d(TAG, "Updating SMS status: " + (success ? "SENT" : "FAILED"));

        // Update the SMS status in the SOS report
        sosReport.setSmsSent(success);
        sosReport.setSmsStatus(success ? "SENT" : "FAILED");

        // Update the document in Firestore
        Map<String, Object> updates = new HashMap<>();
        updates.put("smsSent", success);
        updates.put("smsStatus", success ? "SENT" : "FAILED");

        firestore.collection(COLLECTION_SOS_REPORTS)
                .document(sosReport.getReportId())
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "SMS status updated in Firestore");

                    // Update the SOS status to RECEIVED
                    updateSOSStatus("RECEIVED");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating SMS status in Firestore", e);

                    // Continue with status update even if this fails
                    updateSOSStatus("RECEIVED");
                });
    }

    /**
     * Step 6: Update the SOS status to RECEIVED
     */
    private void updateSOSStatus(String newStatus) {
        Log.d(TAG, "Updating SOS status to: " + newStatus);

        // Update the status in the SOS report
        sosReport.setStatus(newStatus);
        sosReport.setLastStatusUpdate(new Date());

        // Update the document in Firestore
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", newStatus);
        updates.put("lastStatusUpdate", new Date());

        firestore.collection(COLLECTION_SOS_REPORTS)
                .document(sosReport.getReportId())
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "SOS status updated in Firestore");

                    // Also update the status in Realtime Database
                    DatabaseReference statusRef = realtimeDb.getReference(REALTIME_DB_SOS_PATH)
                            .child(sosReport.getReportId())
                            .child("status");
                    statusRef.setValue(newStatus);

                    // SOS processing is complete, notify listener
                    if (processingListener != null) {
                        processingListener.onSOSProcessingComplete(sosReport);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating SOS status in Firestore", e);

                    // SOS processing is complete but with status update error
                    if (processingListener != null) {
                        processingListener.onSOSProcessingComplete(sosReport);
                    }
                });
    }

    /**
     * Interface for SOS processing events
     */
    public interface SOSProcessingListener {
        void onSOSProcessingComplete(SOSReport report);
        void onSOSProcessingFailed(Exception e, SOSReport report);
    }
}