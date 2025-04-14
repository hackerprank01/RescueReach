package com.rescuereach.data.repository;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.rescuereach.data.model.SOSReport;

import java.util.List;

/**
 * Repository interface for SOS emergency report data operations
 */
public interface SOSRepository {

    /**
     * Submit an SOS report and get a Task for more control over operation
     * @param report The SOS report to submit
     * @return Task that completes with the document reference
     */
    Task<DocumentReference> submitSOSReport(SOSReport report);

    /**
     * Save a new SOS emergency report with callback pattern
     * @param report The SOS report to save
     * @param listener Callback for operation result
     */
    void saveSOSReport(SOSReport report, OnReportSavedListener listener);

    /**
     * Update an existing SOS emergency report
     * @param report The SOS report to update
     * @param listener Callback for operation result
     */
    void updateSOSReport(SOSReport report, OnCompleteListener listener);

    /**
     * Update the status of an SOS report
     * @param reportId Report ID
     * @param newStatus New status value
     * @param responderInfo Optional responder information
     * @param listener Callback for operation result
     */
    void updateSOSStatus(String reportId, String newStatus,
                         Object responderInfo, OnCompleteListener listener);

    /**
     * Get a specific SOS report by ID
     * @param reportId The report ID to retrieve
     * @param listener Callback with the retrieved report
     */
    void getSOSReportById(String reportId, OnReportFetchedListener listener);

    /**
     * Get all SOS reports for a specific user
     * @param userId User ID (phone number)
     * @param limit Maximum number of reports to retrieve
     * @param listener Callback with the list of reports
     */
    void getUserSOSReports(String userId, int limit, OnReportListFetchedListener listener);

    /**
     * Get active SOS reports within a region
     * @param state State/region name
     * @param limit Maximum number of reports to retrieve
     * @param listener Callback with the list of reports
     */
    void getActiveSOSReportsByRegion(String state, int limit, OnReportListFetchedListener listener);

    /**
     * Add a comment or update to an SOS report
     * @param reportId Report ID
     * @param comment The comment content
     * @param authorId User ID of the comment author
     * @param listener Callback for operation result
     */
    void addSOSComment(String reportId, String comment, String authorId, OnCompleteListener listener);

    /**
     * Delete an SOS report (typically for administrative purposes)
     * @param reportId Report ID to delete
     * @param listener Callback for operation result
     */
    void deleteSOSReport(String reportId, OnCompleteListener listener);

    // Callback interfaces

    interface OnReportSavedListener {
        void onSuccess(SOSReport report);
        void onError(Exception e);
    }

    interface OnReportFetchedListener {
        void onSuccess(SOSReport report);
        void onError(Exception e);
    }

    interface OnReportListFetchedListener {
        void onSuccess(List<SOSReport> reports);
        void onError(Exception e);
    }
}