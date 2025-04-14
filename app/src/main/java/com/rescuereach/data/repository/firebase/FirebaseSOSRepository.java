package com.rescuereach.data.repository.firebase;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
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

    // Firestore references
    private final FirebaseFirestore firestore;
    private final CollectionReference reportsCollection;
    private final Handler mainHandler;
    private final UserSessionManager sessionManager;
    private final Executor backgroundExecutor;

    /**
     * Create a new FirebaseSOSRepository
     */
    public FirebaseSOSRepository() {
        this.firestore = FirebaseFirestore.getInstance();
        this.reportsCollection = firestore.collection(COLLECTION_SOS_REPORTS);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.sessionManager = UserSessionManager.getInstance(null);
        this.backgroundExecutor = Executors.newSingleThreadExecutor();
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
     * Ensure userId is set on the report to satisfy Firestore security rules
     */
    private void ensureUserIdIsSet(SOSReport report) {
        if ((report.getUserId() == null || report.getUserId().isEmpty()) && sessionManager != null) {
            // Try to get phone number from session manager
            String userId = sessionManager.getSavedPhoneNumber();
            if (userId != null && !userId.isEmpty()) {
                report.setUserId(userId);
            } else {
                // Fallback to FirebaseAuth user ID if available
                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                if (currentUser != null) {
                    report.setUserId(currentUser.getUid());
                }
            }
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
                DocumentReference reportRef = reportsCollection.document(report.getReportId());

                reportRef.set(report, SetOptions.merge())
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "SOS report updated: " + report.getReportId());
                            if (listener != null) {
                                mainHandler.post(listener::onSuccess);
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error updating SOS report", e);
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

                // Start a batch write
                WriteBatch batch = firestore.batch();

                // Update the report status
                Map<String, Object> updates = new HashMap<>();
                updates.put(FIELD_STATUS, newStatus);
                updates.put("statusUpdatedAt", new Date());

                // Add responder info if provided
                if (responderInfo != null) {
                    updates.put(FIELD_RESPONDER_INFO, responderInfo);
                }

                batch.update(reportRef, updates);

                // Update the history record if it exists
                reportRef.get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists()) {
                                SOSReport report = documentSnapshot.toObject(SOSReport.class);
                                if (report != null && report.getUserId() != null) {
                                    // Update the history record
                                    DocumentReference historyRef = firestore
                                            .collection(COLLECTION_SOS_HISTORY)
                                            .document(report.getUserId());

                                    Map<String, Object> historyUpdates = new HashMap<>();
                                    historyUpdates.put("reports." + reportId + "." + FIELD_STATUS, newStatus);
                                    historyUpdates.put("reports." + reportId + ".statusUpdatedAt", new Date());

                                    batch.update(historyRef, historyUpdates);
                                }
                            }

                            // Commit the batch
                            batch.commit()
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d(TAG, "SOS status updated to: " + newStatus);
                                        if (listener != null) {
                                            mainHandler.post(listener::onSuccess);
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Error updating SOS status", e);
                                        if (listener != null) {
                                            mainHandler.post(() -> listener.onError(e));
                                        }
                                    });
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error fetching report for status update", e);
                            if (listener != null) {
                                mainHandler.post(() -> listener.onError(e));
                            }
                        });
            } catch (Exception e) {
                Log.e(TAG, "Error in updateSOSStatus", e);
                if (listener != null) {
                    mainHandler.post(() -> listener.onError(e));
                }
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
                // Use Task API for more efficient handling
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
                            Log.e(TAG, "Error fetching SOS report", e);
                            if (listener != null) {
                                mainHandler.post(() -> listener.onError(e));
                            }
                        });
            } catch (Exception e) {
                Log.e(TAG, "Error in getSOSReportById", e);
                if (listener != null) {
                    mainHandler.post(() -> listener.onError(e));
                }
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
                // Create comment data
                Map<String, Object> commentData = new HashMap<>();
                commentData.put(FIELD_COMMENT, comment);
                commentData.put(FIELD_AUTHOR_ID, authorId);
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
                                            if (listener != null) {
                                                mainHandler.post(listener::onSuccess);
                                            }
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "Error deleting SOS report", e);
                                            if (listener != null) {
                                                mainHandler.post(() -> listener.onError(e));
                                            }
                                        });
                            } else {
                                // Report doesn't exist, consider it a success
                                if (listener != null) {
                                    mainHandler.post(listener::onSuccess);
                                }
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error fetching report for deletion", e);
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
}