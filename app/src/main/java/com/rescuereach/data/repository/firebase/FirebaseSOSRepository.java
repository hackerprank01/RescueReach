package com.rescuereach.data.repository.firebase;

import android.util.Log;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;
import com.rescuereach.data.model.SOSReport;
import com.rescuereach.data.repository.OnCompleteListener;
import com.rescuereach.data.repository.SOSRepository;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * Create a new FirebaseSOSRepository
     */
    public FirebaseSOSRepository() {
        this.firestore = FirebaseFirestore.getInstance();
        this.reportsCollection = firestore.collection(COLLECTION_SOS_REPORTS);
    }

    @Override
    public void saveSOSReport(SOSReport report, OnReportSavedListener listener) {
        Log.d(TAG, "Saving SOS report for user: " + report.getUserId());

        try {
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
                            listener.onSuccess(report);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error saving SOS report", e);
                        if (listener != null) {
                            listener.onError(e);
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error in saveSOSReport", e);
            if (listener != null) {
                listener.onError(e);
            }
        }
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
                listener.onError(new IllegalArgumentException("Invalid report or report ID"));
            }
            return;
        }

        DocumentReference reportRef = reportsCollection.document(report.getReportId());

        reportRef.set(report, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "SOS report updated: " + report.getReportId());
                    if (listener != null) {
                        listener.onSuccess();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating SOS report", e);
                    if (listener != null) {
                        listener.onError(e);
                    }
                });
    }

    @Override
    public void updateSOSStatus(String reportId, String newStatus,
                                Object responderInfo, OnCompleteListener listener) {
        if (reportId == null || reportId.isEmpty() || newStatus == null || newStatus.isEmpty()) {
            if (listener != null) {
                listener.onError(new IllegalArgumentException("Invalid report ID or status"));
            }
            return;
        }

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
                                    listener.onSuccess();
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error updating SOS status", e);
                                if (listener != null) {
                                    listener.onError(e);
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching report for status update", e);
                    if (listener != null) {
                        listener.onError(e);
                    }
                });
    }

// Inside FirebaseSOSRepository - optimize these methods:

    @Override
    public void getSOSReportById(String reportId, OnReportFetchedListener listener) {
        if (reportId == null || reportId.isEmpty()) {
            if (listener != null) {
                listener.onError(new IllegalArgumentException("Invalid report ID"));
            }
            return;
        }

        // Use Task API for more efficient handling
        reportsCollection.document(reportId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Use background thread for serialization
                        Executors.newSingleThreadExecutor().execute(() -> {
                            SOSReport report = documentSnapshot.toObject(SOSReport.class);
                            // Return to calling thread
                            if (listener != null) {
                                listener.onSuccess(report);
                            }
                        });
                    } else {
                        if (listener != null) {
                            listener.onError(new Exception("Report not found"));
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching SOS report", e);
                    if (listener != null) {
                        listener.onError(e);
                    }
                });
    }

    @Override
    public void getUserSOSReports(String userId, int limit, OnReportListFetchedListener listener) {
        if (userId == null || userId.isEmpty()) {
            if (listener != null) {
                listener.onError(new IllegalArgumentException("Invalid user ID"));
            }
            return;
        }

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
                        listener.onSuccess(reports);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching user SOS reports", e);
                    if (listener != null) {
                        listener.onError(e);
                    }
                });
    }

    @Override
    public void getActiveSOSReportsByRegion(String state, int limit, OnReportListFetchedListener listener) {
        if (state == null || state.isEmpty()) {
            if (listener != null) {
                listener.onError(new IllegalArgumentException("Invalid state parameter"));
            }
            return;
        }

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
                        listener.onSuccess(reports);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching active SOS reports by region", e);
                    if (listener != null) {
                        listener.onError(e);
                    }
                });
    }

    @Override
    public void addSOSComment(String reportId, String comment, String authorId, OnCompleteListener listener) {
        if (reportId == null || reportId.isEmpty() || comment == null || comment.isEmpty()) {
            if (listener != null) {
                listener.onError(new IllegalArgumentException("Invalid report ID or comment"));
            }
            return;
        }

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
                                    if (task.isSuccessful()) {
                                        listener.onSuccess();
                                    } else {
                                        listener.onError(task.getException());
                                    }
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error adding comment", e);
                    if (listener != null) {
                        listener.onError(e);
                    }
                });
    }

    @Override
    public void deleteSOSReport(String reportId, OnCompleteListener listener) {
        if (reportId == null || reportId.isEmpty()) {
            if (listener != null) {
                listener.onError(new IllegalArgumentException("Invalid report ID"));
            }
            return;
        }

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
                                        listener.onSuccess();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error deleting SOS report", e);
                                    if (listener != null) {
                                        listener.onError(e);
                                    }
                                });
                    } else {
                        // Report doesn't exist, consider it a success
                        if (listener != null) {
                            listener.onSuccess();
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching report for deletion", e);
                    if (listener != null) {
                        listener.onError(e);
                    }
                });
    }
}