package com.rescuereach.data.repository.firebase;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;
import com.rescuereach.data.model.SOSReport;
import com.rescuereach.data.repository.OnCompleteListener;
import com.rescuereach.data.repository.SOSRepository;
import com.rescuereach.service.auth.UserSessionManager;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Firebase implementation of SOS Repository to store and manage emergency reports
 */
public class FirebaseSOSRepository implements SOSRepository {
    private static final String TAG = "FirebaseSOSRepository";

    // Collection names
    private static final String COLLECTION_SOS_REPORTS = "sos_reports";
    private static final String COLLECTION_SOS_COMMENTS = "comments";
    private static final String COLLECTION_SOS_HISTORY = "sos_history";

    // Realtime Database paths
    private static final String RTDB_SOS_PATH = "sos";
    private static final String RTDB_ACTIVE_SOS_PATH = "active_emergencies";

    // Field names
    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_TIMESTAMP = "timestamp";
    private static final String FIELD_STATE = "state";
    private static final String FIELD_EMERGENCY_TYPE = "emergencyType";
    private static final String FIELD_ONLINE = "isOnline";
    private static final String FIELD_RESPONDER_INFO = "responderInfo";
    private static final String FIELD_AUTHOR_ID = "authorId";
    private static final String FIELD_COMMENT = "comment";
    private static final String FIELD_COMMENT_TIME = "commentTime";
    private static final String FIELD_LAST_UPDATED = "lastUpdated";

    // Firebase instances
    private final FirebaseFirestore firestore;
    private final FirebaseDatabase realtimeDb;
    private final CollectionReference reportsCollection;
    private final DatabaseReference sosRTDBRef;
    private final DatabaseReference activeEmergenciesRef;

    // Utilities
    private final Handler mainHandler;
    private final UserSessionManager sessionManager;
    private final Executor backgroundExecutor;
    private final FirebaseAuth firebaseAuth;

    /**
     * Create a new FirebaseSOSRepository
     */
    public FirebaseSOSRepository() {
        // Initialize Firebase instances
        this.firestore = FirebaseFirestore.getInstance();
        this.realtimeDb = FirebaseDatabase.getInstance();
        this.firebaseAuth = FirebaseAuth.getInstance();

        // Set references
        this.reportsCollection = firestore.collection(COLLECTION_SOS_REPORTS);
        this.sosRTDBRef = realtimeDb.getReference(RTDB_SOS_PATH);
        this.activeEmergenciesRef = realtimeDb.getReference(RTDB_ACTIVE_SOS_PATH);

        // Initialize utilities
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.sessionManager = UserSessionManager.getInstance(null);
        this.backgroundExecutor = Executors.newSingleThreadExecutor();

        // Ensure authentication for Firebase operations
        ensureAuthentication();
    }

    /**
     * Ensure we have authentication for Firebase operations
     */
    private void ensureAuthentication() {
        if (firebaseAuth.getCurrentUser() == null) {
            // Sign in anonymously if no user is signed in
            firebaseAuth.signInAnonymously()
                    .addOnSuccessListener(authResult ->
                            Log.d(TAG, "Anonymous authentication successful"))
                    .addOnFailureListener(e ->
                            Log.e(TAG, "Anonymous authentication failed", e));
        }
    }

    /**
     * Submit an SOS report - returns a Task for better handling
     * @param report The report to submit
     * @return A Task that completes when submission is done
     */
    @Override
    public Task<DocumentReference> submitSOSReport(SOSReport report) {
        // Ensure the report has an ID
        if (report.getReportId() == null || report.getReportId().isEmpty()) {
            DocumentReference newRef = reportsCollection.document();
            report.setReportId(newRef.getId());
        }

        // Set current timestamp if not set
        if (report.getTimestamp() == null) {
            report.setTimestamp(new Date());
        }

        // Set status if not set
        if (report.getStatus() == null || report.getStatus().isEmpty()) {
            report.setStatus(SOSReport.STATUS_PENDING);
        }

        // Make sure userId is set (for security rules)
        ensureUserIdIsSet(report);

        // Create a reference to use
        DocumentReference reportRef = reportsCollection.document(report.getReportId());

        // Save to Realtime Database first
        saveToRealtimeDatabase(report);

        // Return the task for the set operation
        Task<Void> setTask = reportRef.set(report);

        // When set completes, also save to history
        setTask.addOnSuccessListener(aVoid -> {
            // Also save to history collection for the user
            saveToUserHistory(report, report.getReportId());
        });

        // Return a task that resolves to the document reference when complete
        return setTask.continueWith(task -> reportRef);
    }

    @Override
    public void saveSOSReport(SOSReport report, OnReportSavedListener listener) {
        Log.d(TAG, "Saving SOS report for user: " + report.getUserId());

        try {
            // Run on background thread
            backgroundExecutor.execute(() -> {
                try {
                    // Ensure userId is set for Firestore security rules
                    ensureUserIdIsSet(report);

                    // Create a new document reference if we don't have an ID yet
                    final DocumentReference reportRef;
                    if (report.getReportId() == null || report.getReportId().isEmpty()) {
                        reportRef = reportsCollection.document(); // Auto-generate ID
                        report.setReportId(reportRef.getId()); // Set the ID on the report object
                    } else {
                        reportRef = reportsCollection.document(report.getReportId());
                    }

                    // Set or update the current date if not already set
                    if (report.getTimestamp() == null) {
                        report.setTimestamp(new Date());
                    }

                    // Make sure status is set
                    if (report.getStatus() == null) {
                        report.setStatus(SOSReport.STATUS_PENDING);
                    }

                    // Save to Realtime Database first for better real-time access
                    saveToRealtimeDatabase(report);

                    // Save the report to Firestore
                    reportRef.set(report)
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "SOS report saved with ID: " + report.getReportId());

                                // Also save to history collection for the user
                                saveToUserHistory(report, reportRef.getId());

                                if (listener != null) {
                                    mainHandler.post(() -> listener.onSuccess(report));
                                }
                            })
                            .addOnFailureListener(e -> {
                                String errorMsg = "Error saving SOS report: " + e.getMessage();
                                Log.e(TAG, errorMsg, e);

                                if (e instanceof FirebaseFirestoreException) {
                                    FirebaseFirestoreException firestoreException = (FirebaseFirestoreException) e;
                                    if (firestoreException.getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                                        Log.e(TAG, "Permission denied. Check Firestore rules and user auth state");

                                        // Try to authenticate and retry once
                                        authenticateAnonymouslyAndRetry(report, listener);
                                        return;
                                    }
                                }

                                if (listener != null) {
                                    mainHandler.post(() -> listener.onError(e));
                                }
                            });
                } catch (Exception e) {
                    Log.e(TAG, "Error in saveSOSReport", e);
                    if (listener != null) {
                        mainHandler.post(() -> listener.onError(e));
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error dispatching saveSOSReport task", e);
            if (listener != null) {
                mainHandler.post(() -> listener.onError(e));
            }
        }
    }

    /**
     * Save SOS report to Realtime Database for better real-time access
     */
    private void saveToRealtimeDatabase(SOSReport report) {
        if (report == null || report.getReportId() == null) {
            Log.e(TAG, "Cannot save to Realtime DB: Report or report ID is null");
            return;
        }

        try {
            // Create a version of the report suitable for RTDB
            // with custom mapping for GeoPoint which doesn't work in RTDB
            Map<String, Object> rtdbReport = new HashMap<>();

            // Copy basic fields
            rtdbReport.put("reportId", report.getReportId());
            rtdbReport.put("userId", report.getUserId());
            rtdbReport.put("status", report.getStatus());
            rtdbReport.put("emergencyType", report.getEmergencyType());
            rtdbReport.put("isOnline", report.isOnline());
            rtdbReport.put("address", report.getAddress());
            rtdbReport.put("city", report.getCity());
            rtdbReport.put("state", report.getState());

            if (report.getTimestamp() != null) {
                rtdbReport.put("timestamp", report.getTimestamp().getTime());
            }

            // IMPORTANT FIX: Convert GeoPoint to simple Map for RTDB
            if (report.getLocation() != null) {
                Map<String, Double> location = new HashMap<>();
                location.put("latitude", report.getLocation().getLatitude());
                location.put("longitude", report.getLocation().getLongitude());
                rtdbReport.put("location", location);
            }

            // Save to general SOS path
            sosRTDBRef.child(report.getReportId()).setValue(rtdbReport)
                    .addOnSuccessListener(aVoid ->
                            Log.d(TAG, "SOS saved to Realtime Database"))
                    .addOnFailureListener(e ->
                            Log.e(TAG, "Error saving SOS to Realtime Database", e));

            // If the SOS is active (not resolved), also save to active emergencies
            if (!SOSReport.STATUS_RESOLVED.equals(report.getStatus())) {
                // Create a simplified version for active emergencies
                Map<String, Object> activeEmergency = new HashMap<>();
                activeEmergency.put("reportId", report.getReportId());
                activeEmergency.put("userId", report.getUserId());
                activeEmergency.put("emergencyType", report.getEmergencyType());
                activeEmergency.put("status", report.getStatus());
                activeEmergency.put("timestamp", report.getTimestamp() != null ?
                        report.getTimestamp().getTime() : System.currentTimeMillis());

                // Add location if available (using same format as above)
                if (report.getLocation() != null) {
                    Map<String, Double> location = new HashMap<>();
                    location.put("latitude", report.getLocation().getLatitude());
                    location.put("longitude", report.getLocation().getLongitude());
                    activeEmergency.put("location", location);
                }

                // Add address if available
                if (report.getAddress() != null) {
                    activeEmergency.put("address", report.getAddress());
                }

                // Add state/region for filtering
                if (report.getState() != null) {
                    activeEmergency.put("state", report.getState());
                }

                // Save to active emergencies
                activeEmergenciesRef.child(report.getReportId()).setValue(activeEmergency)
                        .addOnSuccessListener(aVoid ->
                                Log.d(TAG, "SOS saved to active emergencies"))
                        .addOnFailureListener(e ->
                                Log.e(TAG, "Error saving SOS to active emergencies", e));
            } else {
                // If resolved, remove from active emergencies
                activeEmergenciesRef.child(report.getReportId()).removeValue();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving to Realtime Database", e);
        }
    }

    /**
     * Ensure userId is set on the report to satisfy Firestore security rules
     */
    private void ensureUserIdIsSet(SOSReport report) {
        if (report.getUserId() == null || report.getUserId().isEmpty()) {
            // First try Firebase Auth UID - most reliable for security rules
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null) {
                report.setUserId(currentUser.getUid());
                Log.d(TAG, "Setting userId from Firebase Auth: " + currentUser.getUid());
                return;
            }

            // Fallback to session manager phone number
            if (sessionManager != null) {
                String phoneNumber = sessionManager.getSavedPhoneNumber();
                if (phoneNumber != null && !phoneNumber.isEmpty()) {
                    report.setUserId(phoneNumber);
                    Log.d(TAG, "Setting userId from phone number: " + phoneNumber);
                    return;
                }
            }

            // Last resort - generate a random ID
            String randomId = "user_" + System.currentTimeMillis();
            report.setUserId(randomId);
            Log.d(TAG, "Setting userId with generated value: " + randomId);
        }
    }

    /**
     * Try to authenticate anonymously and retry the save operation
     */
    private void authenticateAnonymouslyAndRetry(SOSReport report, OnReportSavedListener listener) {
        FirebaseAuth.getInstance().signInAnonymously()
                .addOnSuccessListener(authResult -> {
                    Log.d(TAG, "Anonymous auth success, retrying save");
                    // Update userId with anonymous auth ID
                    if (authResult.getUser() != null) {
                        report.setUserId(authResult.getUser().getUid());
                    }
                    // Retry the save operation
                    saveSOSReport(report, listener);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Anonymous auth failed", e);
                    if (listener != null) {
                        mainHandler.post(() -> listener.onError(new Exception("Authentication failed: " + e.getMessage())));
                    }
                });
    }

    /**
     * Save a reference to the report in the user's history collection
     */
    private void saveToUserHistory(SOSReport report, String reportId) {
        if (report.getUserId() == null || report.getUserId().isEmpty()) {
            Log.w(TAG, "Cannot save to history: No user ID in report");
            return;
        }

        try {
            // Create a reference to the user's history document
            String userId = report.getUserId();
            DocumentReference userHistoryRef = firestore.collection(COLLECTION_SOS_HISTORY).document(userId);

            // Create a map with the new report reference
            Map<String, Object> historyUpdate = new HashMap<>();
            Map<String, Object> reportInfo = new HashMap<>();

            reportInfo.put(FIELD_TIMESTAMP, report.getTimestamp() != null ?
                    report.getTimestamp() : new Date());
            reportInfo.put(FIELD_EMERGENCY_TYPE, report.getEmergencyType());
            reportInfo.put(FIELD_STATUS, report.getStatus());

            if (report.getAddress() != null) {
                reportInfo.put("address", report.getAddress());
            }
            if (report.getState() != null) {
                reportInfo.put("state", report.getState());
            }

            // Add to the reports map using the reportId as the key
            historyUpdate.put("reports." + reportId, reportInfo);
            historyUpdate.put("lastReportTime", report.getTimestamp() != null ?
                    report.getTimestamp() : new Date());
            historyUpdate.put("reportCount", FieldValue.increment(1));

            // Set with merge to update or create the document
            userHistoryRef.set(historyUpdate, SetOptions.merge())
                    .addOnSuccessListener(aVoid ->
                            Log.d(TAG, "Added report to user history: " + userId))
                    .addOnFailureListener(e ->
                            Log.e(TAG, "Error adding to user history", e));

        } catch (Exception e) {
            Log.e(TAG, "Error saving to user history", e);
        }
    }

    @Override
    public void updateSOSReport(SOSReport report, OnCompleteListener listener) {
        if (report == null || report.getReportId() == null || report.getReportId().isEmpty()) {
            if (listener != null) {
                mainHandler.post(() -> listener.onError(new IllegalArgumentException("Invalid report or report ID")));
            }
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                // Ensure userId is set for permissions
                ensureUserIdIsSet(report);

                DocumentReference reportRef = reportsCollection.document(report.getReportId());

                // First update Realtime Database for better real-time access
                saveToRealtimeDatabase(report);

                reportRef.set(report, SetOptions.merge())
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "SOS report updated: " + report.getReportId());
                            if (listener != null) {
                                mainHandler.post(listener::onSuccess);
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error updating SOS report", e);

                            // Try to handle permission errors with authentication
                            if (e instanceof FirebaseFirestoreException &&
                                    ((FirebaseFirestoreException) e).getCode() ==
                                            FirebaseFirestoreException.Code.PERMISSION_DENIED) {

                                authenticateAndRetryUpdate(report, listener);
                                return;
                            }

                            if (listener != null) {
                                mainHandler.post(() -> listener.onError(e));
                            }
                        });
            } catch (Exception e) {
                Log.e(TAG, "Error in updateSOSReport", e);
                if (listener != null) {
                    mainHandler.post(() -> listener.onError(e));
                }
            }
        });
    }

    /**
     * Authenticate and retry update operation
     */
    private void authenticateAndRetryUpdate(SOSReport report, OnCompleteListener listener) {
        FirebaseAuth.getInstance().signInAnonymously()
                .addOnSuccessListener(authResult -> {
                    Log.d(TAG, "Anonymous auth success for update, retrying");

                    // Update the user ID if authenticated
                    if (authResult.getUser() != null &&
                            (report.getUserId() == null || report.getUserId().isEmpty())) {
                        report.setUserId(authResult.getUser().getUid());
                    }

                    // Retry the update
                    updateSOSReport(report, listener);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Anonymous auth failed for update", e);
                    if (listener != null) {
                        mainHandler.post(() -> listener.onError(e));
                    }
                });
    }

    @Override
    public void updateSOSStatus(String reportId, String newStatus,
                                Object responderInfo, OnCompleteListener listener) {
        if (reportId == null || reportId.isEmpty() || newStatus == null || newStatus.isEmpty()) {
            if (listener != null) {
                mainHandler.post(() -> listener.onError(new IllegalArgumentException("Invalid report ID or status")));
            }
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                DocumentReference reportRef = reportsCollection.document(reportId);

                // Get the current report first to update both Firestore and RTDB
                reportRef.get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists()) {
                                SOSReport report = documentSnapshot.toObject(SOSReport.class);
                                if (report != null) {
                                    // Update the status
                                    report.setStatus(newStatus);

                                    // Convert the report to a map manually instead of calling toMap()
                                    Map<String, Object> updateData = convertReportToMap(report);

                                    // Add statusUpdatedAt field
                                    updateData.put("statusUpdatedAt", new Date());

                                    // Add responder info if provided
                                    if (responderInfo != null) {
                                        if (responderInfo instanceof Map) {
                                            // If it's already a Map, put it directly in the update data
                                            updateData.put(FIELD_RESPONDER_INFO, responderInfo);
                                        } else {
                                            // If it's some other object, convert it to String representation
                                            updateData.put(FIELD_RESPONDER_INFO, responderInfo.toString());
                                        }
                                    }

                                    // Use updateData map to update instead of the report object directly
                                    reportRef.set(updateData, SetOptions.merge())
                                            .addOnSuccessListener(aVoid -> {
                                                // Update Realtime Database
                                                updateStatusInRealtimeDatabase(reportId, newStatus, responderInfo);

                                                // Update history
                                                if (report.getUserId() != null) {
                                                    updateStatusInHistory(reportId, report.getUserId(), newStatus);
                                                }

                                                if (listener != null) {
                                                    mainHandler.post(listener::onSuccess);
                                                }
                                            })
                                            .addOnFailureListener(e -> {
                                                Log.e(TAG, "Error updating report with status", e);
                                                if (listener != null) {
                                                    mainHandler.post(() -> listener.onError(e));
                                                }
                                            });
                                } else {
                                    // Document exists but couldn't convert to object
                                    updateStatusDirectly(reportRef, reportId, newStatus, responderInfo, listener);
                                }
                            } else {
                                // Document doesn't exist
                                if (listener != null) {
                                    mainHandler.post(() -> listener.onError(new Exception("Report not found")));
                                }
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error fetching report for status update", e);

                            // Still try direct update as fallback
                            updateStatusDirectly(reportRef, reportId, newStatus, responderInfo, listener);
                        });
            } catch (Exception e) {
                Log.e(TAG, "Error in updateSOSStatus", e);
                if (listener != null) {
                    mainHandler.post(() -> listener.onError(e));
                }
            }
        });
    }

    private Map<String, Object> convertReportToMap(SOSReport report) {
        if (report == null) {
            return new HashMap<>();
        }

        Map<String, Object> map = new HashMap<>();

        // Add all standard fields that are definitely in the SOSReport class
        map.put("reportId", report.getReportId());
        map.put("userId", report.getUserId());
        map.put("status", report.getStatus());
        map.put("emergencyType", report.getEmergencyType());

        if (report.getTimestamp() != null) {
            map.put("timestamp", report.getTimestamp());
        }

        // Add location if available
        if (report.getLocation() != null) {
            // GeoPoint for Firestore
            map.put("location", new GeoPoint(
                    report.getLocation().getLatitude(),
                    report.getLocation().getLongitude()
            ));
        }

        // Add other fields if they exist
        if (report.getAddress() != null) {
            map.put("address", report.getAddress());
        }

        if (report.getCity() != null) {
            map.put("city", report.getCity());
        }

        if (report.getState() != null) {
            map.put("state", report.getState());
        }

        // REMOVED: postalCode field since getPostalCode() doesn't exist

        // Add emergency contacts if available
        if (report.getEmergencyContactNumbers() != null && !report.getEmergencyContactNumbers().isEmpty()) {
            map.put("emergencyContactNumbers", report.getEmergencyContactNumbers());
        }

        // Add any user info
        if (report.getUserInfo() != null && !report.getUserInfo().isEmpty()) {
            map.put("userInfo", report.getUserInfo());
        }

        // Add SMS status fields if they exist
        try {
            map.put("smsSent", report.isSmsSent());
            map.put("smsStatus", report.getSmsStatus());
        } catch (NoSuchMethodError e) {
            // These methods might not exist, which is fine
        }

        // Add online status
        map.put("isOnline", report.isOnline());

        return map;
    }

    /**
     * Update status directly without fetching the report first
     */
    private void updateStatusDirectly(DocumentReference reportRef, String reportId,
                                      String newStatus, Object responderInfo, OnCompleteListener listener) {
        try {
            // Start a batch write
            WriteBatch batch = firestore.batch();

            // Update the report status
            Map<String, Object> updates = new HashMap<>();
            updates.put(FIELD_STATUS, newStatus);
            updates.put("statusUpdatedAt", new Date());

            // Add last updated by info using current auth user
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null) {
                updates.put("lastUpdatedBy", currentUser.getUid());
            }

            // Add responder info if provided
            if (responderInfo != null) {
                // Handle the responderInfo based on its type
                if (responderInfo instanceof Map) {
                    updates.put(FIELD_RESPONDER_INFO, responderInfo);
                } else {
                    updates.put(FIELD_RESPONDER_INFO, responderInfo.toString());
                }
            }

            batch.update(reportRef, updates);

            // Update Realtime Database too
            updateStatusInRealtimeDatabase(reportId, newStatus, responderInfo);

            // Commit the batch
            batch.commit()
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "SOS status updated directly to: " + newStatus);
                        if (listener != null) {
                            mainHandler.post(listener::onSuccess);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error updating SOS status directly", e);

                        // If permission denied, try anonymous auth and retry
                        if (e instanceof FirebaseFirestoreException &&
                                ((FirebaseFirestoreException) e).getCode() ==
                                        FirebaseFirestoreException.Code.PERMISSION_DENIED) {

                            authenticateAndRetryStatusUpdate(reportId, newStatus, responderInfo, listener);
                            return;
                        }

                        if (listener != null) {
                            mainHandler.post(() -> listener.onError(e));
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error in updateStatusDirectly", e);
            if (listener != null) {
                mainHandler.post(() -> listener.onError(e));
            }
        }
    }

    /**
     * Update status in Realtime Database
     */
    private void updateStatusInRealtimeDatabase(String reportId, String newStatus, Object responderInfo) {
        try {
            // Update in general SOS path
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", newStatus);
            updates.put("statusUpdatedAt", new Date().getTime());

            if (responderInfo != null) {
                // Handle responderInfo based on its type
                if (responderInfo instanceof Map) {
                    updates.put("responderInfo", responderInfo);
                } else {
                    updates.put("responderInfo", responderInfo.toString());
                }
            }

            // Update general SOS entry
            sosRTDBRef.child(reportId).updateChildren(updates)
                    .addOnFailureListener(e ->
                            Log.e(TAG, "Error updating status in RTDB", e));

            // Update or remove from active emergencies based on status
            if (SOSReport.STATUS_RESOLVED.equals(newStatus)) {
                // If resolved, remove from active emergencies
                activeEmergenciesRef.child(reportId).removeValue()
                        .addOnFailureListener(e ->
                                Log.e(TAG, "Error removing from active emergencies", e));
            } else {
                // Otherwise update status in active emergencies
                activeEmergenciesRef.child(reportId).updateChildren(updates)
                        .addOnFailureListener(e ->
                                Log.e(TAG, "Error updating status in active emergencies", e));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating status in RTDB", e);
        }
    }

    /**
     * Update status in user history collection
     */
    private void updateStatusInHistory(String reportId, String userId, String newStatus) {
        try {
            DocumentReference historyRef = firestore
                    .collection(COLLECTION_SOS_HISTORY)
                    .document(userId);

            Map<String, Object> historyUpdates = new HashMap<>();
            historyUpdates.put("reports." + reportId + "." + FIELD_STATUS, newStatus);
            historyUpdates.put("reports." + reportId + ".statusUpdatedAt", new Date());

            historyRef.update(historyUpdates)
                    .addOnFailureListener(e ->
                            Log.e(TAG, "Error updating status in history", e));
        } catch (Exception e) {
            Log.e(TAG, "Error updating status in history", e);
        }
    }

    /**
     * Authenticate and retry status update
     */
    private void authenticateAndRetryStatusUpdate(String reportId, String newStatus,
                                                  Object responderInfo, OnCompleteListener listener) {
        FirebaseAuth.getInstance().signInAnonymously()
                .addOnSuccessListener(authResult -> {
                    Log.d(TAG, "Anonymous auth success for status update, retrying");
                    updateSOSStatus(reportId, newStatus, responderInfo, listener);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Anonymous auth failed for status update", e);
                    if (listener != null) {
                        mainHandler.post(() -> listener.onError(e));
                    }
                });
    }

    @Override
    public void getSOSReportById(String reportId, OnReportFetchedListener listener) {
        if (reportId == null || reportId.isEmpty()) {
            if (listener != null) {
                mainHandler.post(() -> listener.onError(new IllegalArgumentException("Invalid report ID")));
            }
            return;
        }

        // Execute on background thread
        backgroundExecutor.execute(() -> {
            try {
                // First try to get from Realtime Database for better performance
                sosRTDBRef.child(reportId).get()
                        .addOnSuccessListener(dataSnapshot -> {
                            if (dataSnapshot.exists()) {
                                try {
                                    // Convert to SOSReport
                                    SOSReport report = dataSnapshot.getValue(SOSReport.class);
                                    if (report != null) {
                                        // Return to main thread
                                        if (listener != null) {
                                            mainHandler.post(() -> listener.onSuccess(report));
                                        }
                                        return;
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error converting RTDB data to SOSReport", e);
                                    // Fall through to Firestore
                                }
                            }

                            // If not found in RTDB or conversion failed, try Firestore
                            getReportFromFirestore(reportId, listener);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error fetching report from RTDB", e);
                            // Fall through to Firestore
                            getReportFromFirestore(reportId, listener);
                        });
            } catch (Exception e) {
                Log.e(TAG, "Error in getSOSReportById", e);
                // Try Firestore as fallback
                getReportFromFirestore(reportId, listener);
            }
        });
    }

    /**
     * Get report from Firestore (used as fallback if RTDB fails)
     */
    private void getReportFromFirestore(String reportId, OnReportFetchedListener listener) {
        reportsCollection.document(reportId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        try {
                            SOSReport report = documentSnapshot.toObject(SOSReport.class);
                            // Return to main thread
                            if (listener != null) {
                                mainHandler.post(() -> listener.onSuccess(report));
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error converting document to SOSReport", e);
                            if (listener != null) {
                                mainHandler.post(() -> listener.onError(e));
                            }
                        }
                    } else {
                        if (listener != null) {
                            mainHandler.post(() -> listener.onError(new Exception("Report not found")));
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching SOS report from Firestore", e);

                    if (e instanceof FirebaseFirestoreException &&
                            ((FirebaseFirestoreException) e).getCode() ==
                                    FirebaseFirestoreException.Code.PERMISSION_DENIED) {

                        authenticateAndRetryGetReport(reportId, listener);
                        return;
                    }

                    if (listener != null) {
                        mainHandler.post(() -> listener.onError(e));
                    }
                });
    }

    /**
     * Authenticate and retry getting report
     */
    private void authenticateAndRetryGetReport(String reportId, OnReportFetchedListener listener) {
        FirebaseAuth.getInstance().signInAnonymously()
                .addOnSuccessListener(authResult -> {
                    Log.d(TAG, "Anonymous auth success for get report, retrying");
                    getSOSReportById(reportId, listener);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Anonymous auth failed for get report", e);
                    if (listener != null) {
                        mainHandler.post(() -> listener.onError(e));
                    }
                });
    }

    @Override
    public void getUserSOSReports(String userId, int limit, OnReportListFetchedListener listener) {
        if (userId == null || userId.isEmpty()) {
            if (listener != null) {
                mainHandler.post(() -> listener.onError(new IllegalArgumentException("Invalid user ID")));
            }
            return;
        }

        // Execute on background thread
        backgroundExecutor.execute(() -> {
            try {
                Query query = reportsCollection
                        .whereEqualTo(FIELD_USER_ID, userId)
                        .orderBy(FIELD_TIMESTAMP, Query.Direction.DESCENDING);

                if (limit > 0) {
                    query = query.limit(limit);
                }

                query.get()
                        .addOnSuccessListener(queryDocumentSnapshots -> {
                            List<SOSReport> reports = new ArrayList<>();
                            for (DocumentSnapshot doc : queryDocumentSnapshots) {
                                SOSReport report = doc.toObject(SOSReport.class);
                                if (report != null) {
                                    reports.add(report);
                                }
                            }

                            if (listener != null) {
                                mainHandler.post(() -> listener.onSuccess(reports));
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error fetching user SOS reports", e);

                            if (e instanceof FirebaseFirestoreException &&
                                    ((FirebaseFirestoreException) e).getCode() ==
                                            FirebaseFirestoreException.Code.PERMISSION_DENIED) {

                                authenticateAndRetryGetUserReports(userId, limit, listener);
                                return;
                            }

                            if (listener != null) {
                                mainHandler.post(() -> listener.onError(e));
                            }
                        });
            } catch (Exception e) {
                Log.e(TAG, "Error in getUserSOSReports", e);
                if (listener != null) {
                    mainHandler.post(() -> listener.onError(e));
                }
            }
        });
    }

    /**
     * Authenticate and retry getting user reports
     */
    private void authenticateAndRetryGetUserReports(String userId, int limit, OnReportListFetchedListener listener) {
        FirebaseAuth.getInstance().signInAnonymously()
                .addOnSuccessListener(authResult -> {
                    Log.d(TAG, "Anonymous auth success for get user reports, retrying");
                    getUserSOSReports(userId, limit, listener);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Anonymous auth failed for get user reports", e);
                    if (listener != null) {
                        mainHandler.post(() -> listener.onError(e));
                    }
                });
    }

    @Override
    public void getActiveSOSReportsByRegion(String state, int limit, OnReportListFetchedListener listener) {
        if (state == null || state.isEmpty()) {
            if (listener != null) {
                mainHandler.post(() -> listener.onError(new IllegalArgumentException("Invalid state parameter")));
            }
            return;
        }

        // Execute on background thread
        backgroundExecutor.execute(() -> {
            try {
                // Get reports that are not resolved and match the state
                Query query = reportsCollection
                        .whereEqualTo(FIELD_STATE, state)
                        .whereNotEqualTo(FIELD_STATUS, SOSReport.STATUS_RESOLVED)
                        .orderBy(FIELD_STATUS)
                        .orderBy(FIELD_TIMESTAMP, Query.Direction.DESCENDING);

                if (limit > 0) {
                    query = query.limit(limit);
                }

                query.get()
                        .addOnSuccessListener(queryDocumentSnapshots -> {
                            List<SOSReport> reports = new ArrayList<>();
                            for (DocumentSnapshot doc : queryDocumentSnapshots) {
                                SOSReport report = doc.toObject(SOSReport.class);
                                if (report != null) {
                                    reports.add(report);
                                }
                            }

                            if (listener != null) {
                                mainHandler.post(() -> listener.onSuccess(reports));
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error fetching active SOS reports by region", e);

                            if (e instanceof FirebaseFirestoreException &&
                                    ((FirebaseFirestoreException) e).getCode() ==
                                            FirebaseFirestoreException.Code.PERMISSION_DENIED) {

                                authenticateAndRetryGetRegionReports(state, limit, listener);
                                return;
                            }

                            if (listener != null) {
                                mainHandler.post(() -> listener.onError(e));
                            }
                        });
            } catch (Exception e) {
                Log.e(TAG, "Error in getActiveSOSReportsByRegion", e);
                if (listener != null) {
                    mainHandler.post(() -> listener.onError(e));
                }
            }
        });
    }

    /**
     * Authenticate and retry getting region reports
     */
    private void authenticateAndRetryGetRegionReports(String state, int limit, OnReportListFetchedListener listener) {
        FirebaseAuth.getInstance().signInAnonymously()
                .addOnSuccessListener(authResult -> {
                    Log.d(TAG, "Anonymous auth success for get region reports, retrying");
                    getActiveSOSReportsByRegion(state, limit, listener);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Anonymous auth failed for get region reports", e);
                    if (listener != null) {
                        mainHandler.post(() -> listener.onError(e));
                    }
                });
    }

    @Override
    public void addSOSComment(String reportId, String comment, String authorId, OnCompleteListener listener) {
        if (reportId == null || reportId.isEmpty() || comment == null || comment.isEmpty()) {
            if (listener != null) {
                mainHandler.post(() -> listener.onError(new IllegalArgumentException("Invalid report ID or comment")));
            }
            return;
        }

        // Execute on background thread
        backgroundExecutor.execute(() -> {
            try {
                // Use current user ID if not provided
                final String finalAuthorId;
                if (authorId == null || authorId.isEmpty()) {
                    FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                    if (currentUser != null) {
                        finalAuthorId = currentUser.getUid();
                    } else if (sessionManager != null) {
                        finalAuthorId = sessionManager.getSavedPhoneNumber();
                    } else {
                        finalAuthorId = "anonymous_" + System.currentTimeMillis();
                    }
                } else {
                    finalAuthorId = authorId;
                }

                // Create comment data
                Map<String, Object> commentData = new HashMap<>();
                commentData.put(FIELD_COMMENT, comment);
                commentData.put(FIELD_AUTHOR_ID, finalAuthorId);
                commentData.put(FIELD_COMMENT_TIME, new Date());

                // Add to comments subcollection
                reportsCollection
                        .document(reportId)
                        .collection(COLLECTION_SOS_COMMENTS)
                        .add(commentData)
                        .addOnSuccessListener(documentReference -> {
                            Log.d(TAG, "Comment added to report: " + reportId);

                            // Also update the report's last update time
                            reportsCollection.document(reportId)
                                    .update("lastUpdateTime", new Date())
                                    .addOnCompleteListener(task -> {
                                        if (listener != null) {
                                            mainHandler.post(() -> {
                                                if (task.isSuccessful()) {
                                                    listener.onSuccess();
                                                } else {
                                                    listener.onError(task.getException());
                                                }
                                            });
                                        }
                                    });
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error adding comment", e);

                            if (e instanceof FirebaseFirestoreException &&
                                    ((FirebaseFirestoreException) e).getCode() ==
                                            FirebaseFirestoreException.Code.PERMISSION_DENIED) {

                                final String retryAuthorId = finalAuthorId;
                                authenticateAndRetryAddComment(reportId, comment, retryAuthorId, listener);
                                return;
                            }

                            if (listener != null) {
                                mainHandler.post(() -> listener.onError(e));
                            }
                        });
            } catch (Exception e) {
                Log.e(TAG, "Error in addSOSComment", e);
                if (listener != null) {
                    mainHandler.post(() -> listener.onError(e));
                }
            }
        });
    }

    /**
     * Authenticate and retry adding comment
     */
    private void authenticateAndRetryAddComment(String reportId, String comment,
                                                String authorId, OnCompleteListener listener) {
        FirebaseAuth.getInstance().signInAnonymously()
                .addOnSuccessListener(authResult -> {
                    Log.d(TAG, "Anonymous auth success for adding comment, retrying");

                    final String newAuthorId;
                    if (authResult.getUser() != null &&
                            (authorId == null || authorId.isEmpty())) {
                        newAuthorId = authResult.getUser().getUid();
                    } else {
                        newAuthorId = authorId;
                    }

                    addSOSComment(reportId, comment, newAuthorId, listener);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Anonymous auth failed for adding comment", e);
                    if (listener != null) {
                        mainHandler.post(() -> listener.onError(e));
                    }
                });
    }

    @Override
    public void deleteSOSReport(String reportId, OnCompleteListener listener) {
        if (reportId == null || reportId.isEmpty()) {
            if (listener != null) {
                mainHandler.post(() -> listener.onError(new IllegalArgumentException("Invalid report ID")));
            }
            return;
        }

        // Execute on background thread
        backgroundExecutor.execute(() -> {
            try {
                // First get the report to find the user ID for history cleanup
                reportsCollection.document(reportId).get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists()) {
                                SOSReport report = documentSnapshot.toObject(SOSReport.class);
                                String userId = report != null ? report.getUserId() : null;

                                // Start a batch delete
                                WriteBatch batch = firestore.batch();

                                // Delete the report
                                batch.delete(reportsCollection.document(reportId));

                                // Remove from user history if we have a user ID
                                if (userId != null && !userId.isEmpty()) {
                                    DocumentReference historyRef = firestore
                                            .collection(COLLECTION_SOS_HISTORY)
                                            .document(userId);

                                    // Remove this report from the map
                                    Map<String, Object> updates = new HashMap<>();
                                    updates.put("reports." + reportId, FieldValue.delete());
                                    updates.put("reportCount", FieldValue.increment(-1));

                                    batch.update(historyRef, updates);
                                }

                                // Commit all deletions
                                batch.commit()
                                        .addOnSuccessListener(aVoid -> {
                                            Log.d(TAG, "SOS report deleted: " + reportId);

                                            // Also remove from Realtime Database
                                            deleteFromRealtimeDatabase(reportId);

                                            if (listener != null) {
                                                mainHandler.post(listener::onSuccess);
                                            }
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "Error deleting SOS report", e);

                                            if (e instanceof FirebaseFirestoreException &&
                                                    ((FirebaseFirestoreException) e).getCode() ==
                                                            FirebaseFirestoreException.Code.PERMISSION_DENIED) {

                                                authenticateAndRetryDelete(reportId, listener);
                                                return;
                                            }

                                            if (listener != null) {
                                                mainHandler.post(() -> listener.onError(e));
                                            }
                                        });
                            } else {
                                // Report doesn't exist, consider it a success
                                deleteFromRealtimeDatabase(reportId);

                                if (listener != null) {
                                    mainHandler.post(listener::onSuccess);
                                }
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error fetching report for deletion", e);

                            if (e instanceof FirebaseFirestoreException &&
                                    ((FirebaseFirestoreException) e).getCode() ==
                                            FirebaseFirestoreException.Code.PERMISSION_DENIED) {

                                authenticateAndRetryDelete(reportId, listener);
                                return;
                            }

                            if (listener != null) {
                                mainHandler.post(() -> listener.onError(e));
                            }
                        });
            } catch (Exception e) {
                Log.e(TAG, "Error in deleteSOSReport", e);
                if (listener != null) {
                    mainHandler.post(() -> listener.onError(e));
                }
            }
        });
    }

    /**
     * Delete SOS report from Realtime Database
     */
    private void deleteFromRealtimeDatabase(String reportId) {
        try {
            // Delete from general SOS path
            sosRTDBRef.child(reportId).removeValue()
                    .addOnFailureListener(e ->
                            Log.e(TAG, "Error deleting from RTDB", e));

            // Delete from active emergencies
            activeEmergenciesRef.child(reportId).removeValue()
                    .addOnFailureListener(e ->
                            Log.e(TAG, "Error deleting from active emergencies", e));
        } catch (Exception e) {
            Log.e(TAG, "Error deleting from RTDB", e);
        }
    }

    /**
     * Authenticate and retry delete operation
     */
    private void authenticateAndRetryDelete(String reportId, OnCompleteListener listener) {
        FirebaseAuth.getInstance().signInAnonymously()
                .addOnSuccessListener(authResult -> {
                    Log.d(TAG, "Anonymous auth success for delete, retrying");
                    deleteSOSReport(reportId, listener);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Anonymous auth failed for delete", e);
                    if (listener != null) {
                        mainHandler.post(() -> listener.onError(e));
                    }
                });
    }
}