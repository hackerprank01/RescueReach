package com.rescuereach.citizen.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.rescuereach.R;
import com.rescuereach.data.model.SOSReport;
import com.rescuereach.data.repository.RepositoryProvider;
import com.rescuereach.data.repository.SOSRepository;
import com.rescuereach.service.sos.SOSProcessingService;
import com.rescuereach.util.TimeUtils;
import com.rescuereach.util.ToastUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Dialog to display SOS status and updates
 * Shows real-time status updates from Firestore
 */
public class SOSStatusDialog extends Dialog {
    private static final String TAG = "SOSStatusDialog";

    private Handler uiHandler = new Handler(Looper.getMainLooper());

    // SharedPreferences for persistent state
    private SharedPreferences prefs;
    private static final String PREFS_NAME = "sos_dialog_prefs";
    private static final String KEY_ACTIVE_SOS = "has_active_sos";
    private static final String KEY_SOS_ID = "active_sos_report_id";
    private static final String KEY_MINIMIZED = "sos_dialog_minimized";

    // UI components
    private ImageView imageEmergencyType;
    private TextView textEmergencyType;
    private TextView textReportId;
    private TextView textStatus;
    private TextView textStatusDescription;
    private TextView textLocation;
    private TextView textTimestamp;
    private TextView textLastUpdate;
    private ProgressBar progressBar;
    private LinearLayout statusUpdatesContainer;
    private CardView cardResponderInfo;
    private TextView textResponderName;
    private TextView textResponderMessage;
    private MaterialButton buttonClose;
    private MaterialButton buttonCancel;

    // Data
    private SOSReport report;
    private String reportId;
    private SOSRepository sosRepository;
    private SOSProcessingService sosProcessingService;
    private ListenerRegistration reportListener;
    private SOSStatusDialogListener listener;
    private boolean isClosed = false;

    // Constants
    private static final int STATUS_CHECK_INTERVAL = 10000; // 10 seconds

    /**
     * Create a new SOSStatusDialog for an existing report ID
     * @param context The context
     * @param reportId The report ID to track
     */
    public SOSStatusDialog(@NonNull Context context, String reportId) {
        super(context, R.style.Theme_RescueReach_Dialog);
        this.reportId = reportId;
        this.sosRepository = RepositoryProvider.getSOSRepository();
        this.sosProcessingService = new SOSProcessingService(context);
        this.uiHandler = new Handler(Looper.getMainLooper());

        // Initialize SharedPreferences
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Reset minimize state immediately to ensure consistent behavior
        this.prefs.edit().putBoolean(KEY_MINIMIZED, false).apply();
    }

    /**
     * Create a new SOSStatusDialog with a report object
     * @param context The context
     * @param report The SOS report
     */
    public SOSStatusDialog(@NonNull Context context, SOSReport report) {
        super(context, R.style.Theme_RescueReach_Dialog);
        this.report = report;
        this.reportId = report.getReportId();
        this.sosRepository = RepositoryProvider.getSOSRepository();
        this.sosProcessingService = new SOSProcessingService(context);
        this.uiHandler = new Handler(Looper.getMainLooper());

        // Initialize SharedPreferences
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Set a listener for dialog events
     * @param listener The listener
     */
    public void setSOSStatusDialogListener(SOSStatusDialogListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_sos_status);

        // Set dialog properties
        setCancelable(false);
        setCanceledOnTouchOutside(false);

        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

            // Set dim amount for background
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.dimAmount = 0.7f;
            getWindow().setAttributes(params);
        }

        // Initialize UI
        initializeViews();
        setupClickListeners();

        // Reset minimized state when showing dialog
        prefs.edit().putBoolean(KEY_MINIMIZED, false).apply();

        // Load the report - checking for canceled status first
        if (report != null) {
            // Check if report is already canceled/resolved
            boolean isFinalStatus = SOSReport.STATUS_CANCELED.equals(report.getStatus()) ||
                    SOSReport.STATUS_RESOLVED.equals(report.getStatus());

            if (isFinalStatus) {
                // Report is already in final state, clear state and dismiss after showing briefly
                updateUI(report);

                // Show status briefly then dismiss
                uiHandler.postDelayed(() -> {
                    clearSOSState();
                    dismiss();
                }, 3000);
            } else {
                // Normal flow for active reports
                updateUI(report);
                startStatusListener();
            }
        } else if (reportId != null && !reportId.isEmpty()) {
            loadReport(reportId);
        } else {
            handleError("Invalid report or report ID");
        }
    }


    private void initializeViews() {
        imageEmergencyType = findViewById(R.id.image_emergency_type);
        textEmergencyType = findViewById(R.id.text_emergency_type);
        textReportId = findViewById(R.id.text_report_id);
        textStatus = findViewById(R.id.text_status);
        textStatusDescription = findViewById(R.id.text_status_description);
        textLocation = findViewById(R.id.text_location);
        textTimestamp = findViewById(R.id.text_timestamp);
        textLastUpdate = findViewById(R.id.text_last_update);
        progressBar = findViewById(R.id.progress_bar);
        statusUpdatesContainer = findViewById(R.id.status_updates_container);
        cardResponderInfo = findViewById(R.id.card_responder_info);
        textResponderName = findViewById(R.id.text_responder_name);
        textResponderMessage = findViewById(R.id.text_responder_message);
        buttonClose = findViewById(R.id.button_close);
        buttonCancel = findViewById(R.id.button_cancel);

        // Initially hide responder info
        cardResponderInfo.setVisibility(View.GONE);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Apply window flags to improve responsiveness
        if (getWindow() != null) {
            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            );

            // Ensure dialog appears in front
            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
            );
        }
    }
    private void setupClickListeners() {
        // Set up minimize/close button with improved ONE-CLICK functionality
        buttonClose.setOnClickListener(new View.OnClickListener() {
            // Use flag to prevent double-click processing
            private boolean isProcessing = false;

            @Override
            public void onClick(View v) {
                // Prevent multiple rapid clicks
                if (isProcessing) return;
                isProcessing = true;

                Log.d(TAG, "Close/minimize button clicked");

                // Save the active report ID to preferences BEFORE dismissing
                prefs.edit()
                        .putBoolean(KEY_ACTIVE_SOS, true)
                        .putString(KEY_SOS_ID, reportId)
                        .putBoolean(KEY_MINIMIZED, true)
                        .apply();

                Log.d(TAG, "SOS dialog minimized: " + reportId);

                // Schedule dismiss on next frame to ensure preferences are saved
                v.post(() -> dismiss());
            }
        });

        // Set up cancel button with improved error handling
        buttonCancel.setOnClickListener(v -> {
            Log.d(TAG, "Cancel button clicked");

            // Check if cancellation is allowed based on current status
            if (report != null && !isStatusFinal(report.getStatus())) {
                cancelEmergency();
            } else {
                dismiss();
            }
        });
    }

    // Helper method to check if status is final
    private boolean isStatusFinal(String status) {
        return SOSReport.STATUS_RESOLVED.equals(status) ||
                SOSReport.STATUS_CANCELED.equals(status);
    }

    /**
     * Load report from Firebase using its ID
     */
    private void loadReport(String reportId) {
        progressBar.setVisibility(View.VISIBLE);

        // Use background executor for Firebase operations
        new Thread(() -> {
            sosRepository.getSOSReportById(reportId, new SOSRepository.OnReportFetchedListener() {
                @Override
                public void onSuccess(final SOSReport loadedReport) {
                    if (isClosed) return;

                    // Return to main thread for UI updates
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (isClosed) return;

                        report = loadedReport;
                        progressBar.setVisibility(View.GONE);

                        if (report != null) {
                            // Check if report is already canceled/resolved
                            boolean isFinalStatus = SOSReport.STATUS_CANCELED.equals(report.getStatus()) ||
                                    SOSReport.STATUS_RESOLVED.equals(report.getStatus());

                            if (isFinalStatus) {
                                // Report is already in final state
                                updateUI(report);

                                // Show status briefly then dismiss
                                uiHandler.postDelayed(() -> {
                                    clearSOSState();
                                    dismiss();
                                }, 3000);
                            } else {
                                // Normal flow for active reports
                                updateUI(report);
                                startStatusListener();
                            }
                        } else {
                            handleError("Report not found");
                        }
                    });
                }

                @Override
                public void onError(final Exception e) {
                    if (isClosed) return;

                    // Return to main thread for UI updates
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (isClosed) return;

                        Log.e(TAG, "Error loading report", e);
                        progressBar.setVisibility(View.GONE);
                        handleError("Failed to load emergency information: " + e.getMessage());

                        // Clear state on error
                        clearSOSState();
                    });
                }
            });
        }).start();
    }


    /**
     * Start a Firebase listener for real-time status updates
     */
    private void startStatusListener() {
        if (reportId == null || reportId.isEmpty() || isClosed) return;

        try {
            DocumentReference reportRef = FirebaseFirestore.getInstance()
                    .collection("sos_reports")
                    .document(reportId);

            reportListener = reportRef.addSnapshotListener((snapshot, error) -> {
                // Handle on separate thread to avoid UI blocking
                if (error != null) {
                    Log.e(TAG, "Error listening for report updates", error);
                    return;
                }

                if (isClosed) {
                    // Don't process updates after dialog is closed
                    return;
                }

                if (snapshot != null && snapshot.exists()) {
                    // Process the update on a background thread
                    new Thread(() -> {
                        try {
                            final SOSReport updatedReport = snapshot.toObject(SOSReport.class);
                            if (updatedReport != null) {
                                // Check if status actually changed
                                final boolean statusChanged = report == null ||
                                        !updatedReport.getStatus().equals(report.getStatus());

                                // Update our reference
                                report = updatedReport;

                                // Update UI on main thread
                                uiHandler.post(() -> {
                                    if (!isClosed) {
                                        updateUI(updatedReport);

                                        // Notify listener of status changes
                                        if (statusChanged && listener != null) {
                                            listener.onStatusChanged(reportId, updatedReport.getStatus());

                                            // AUTO-CLOSE: If status is CANCELED or RESOLVED, close the dialog
                                            if (SOSReport.STATUS_CANCELED.equals(updatedReport.getStatus()) ||
                                                    SOSReport.STATUS_RESOLVED.equals(updatedReport.getStatus())) {

                                                Log.d(TAG, "Auto-closing dialog due to final status: " +
                                                        updatedReport.getStatus());

                                                // Show a message first
                                                ToastUtil.showShort(getContext(),
                                                        getContext().getString(
                                                                SOSReport.STATUS_CANCELED.equals(updatedReport.getStatus()) ?
                                                                        R.string.emergency_cancelled :
                                                                        R.string.emergency_resolved
                                                        )
                                                );

                                                // Wait 2 seconds before closing to allow user to see the status
                                                uiHandler.postDelayed(() -> {
                                                    // Clear SOS state
                                                    clearSOSState();
                                                    // Dismiss dialog
                                                    dismiss();
                                                }, 2000);
                                            }
                                        }
                                    }
                                });
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing snapshot update", e);
                        }
                    }).start();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up report listener", e);
        }
    }

    /**
     * Update the UI with report information
     */
    private void updateUI(SOSReport report) {
        if (report == null || isClosed) return;

        // Batch UI operations to reduce layout passes
        final Runnable uiUpdate = () -> {
            // Emergency type info
            String emergencyType = report.getEmergencyType();
            int iconRes;
            int typeStringRes;

            switch (emergencyType) {
                case "POLICE":
                    iconRes = R.drawable.ic_police;
                    textEmergencyType.setText(R.string.type_police);
                    break;
                case "FIRE":
                    iconRes = R.drawable.ic_fire;
                    textEmergencyType.setText(R.string.type_fire);
                    break;
                case "MEDICAL":
                    iconRes = R.drawable.ic_medical;
                    textEmergencyType.setText(R.string.type_medical);
                    break;
                default:
                    iconRes = R.drawable.ic_sos_emergency;
                    textEmergencyType.setText(R.string.type_emergency);
                    break;
            }

            imageEmergencyType.setImageResource(iconRes);

            // Update report ID (truncated for display)
            String reportIdDisplay = reportId;
            if (reportIdDisplay != null && reportIdDisplay.length() > 8) {
                reportIdDisplay = reportIdDisplay.substring(0, 8) + "...";
            }
            textReportId.setText(getContext().getString(R.string.report_id_format, reportIdDisplay));

            // Update status
            updateStatus(report.getStatus());

            // Update location
            if (report.getAddress() != null && !report.getAddress().isEmpty()) {
                textLocation.setText(report.getAddress());
            } else if (report.getLocation() != null) {
                String coordinates = String.format(Locale.US, "%.6f, %.6f",
                        report.getLocation().getLatitude(), report.getLocation().getLongitude());
                textLocation.setText(coordinates);
            } else {
                textLocation.setText(R.string.unknown_location);
            }

            // Update timestamp
            if (report.getTimestamp() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault());
                textTimestamp.setText(sdf.format(report.getTimestamp()));
            } else {
                textTimestamp.setText(R.string.unknown);
            }

            // Update last status change time
            if (report.getStatusUpdatedAt() != null) {
                String timeAgo = TimeUtils.getTimeAgo(report.getStatusUpdatedAt(), getContext());
                textLastUpdate.setText(getContext().getString(R.string.last_update_format, timeAgo));
            } else {
                textLastUpdate.setText(getContext().getString(R.string.last_update_format, "just now"));
            }

            // Update responder info if available - only change visibility when needed
            boolean showResponderInfo = report.getResponderInfo() != null &&
                    !report.getResponderInfo().isEmpty() &&
                    !report.getStatus().equals(SOSReport.STATUS_PENDING);

            if ((cardResponderInfo.getVisibility() == View.VISIBLE) != showResponderInfo) {
                cardResponderInfo.setVisibility(showResponderInfo ? View.VISIBLE : View.GONE);
            }

            if (showResponderInfo) {
                // Only update responder content if visible
                Object responderNameObj = report.getResponderInfo().get("responderName");
                if (responderNameObj != null) {
                    textResponderName.setText(responderNameObj.toString());
                } else {
                    textResponderName.setText(R.string.emergency_services);
                }

                // Show a message based on the status
                switch (report.getStatus()) {
                    case SOSReport.STATUS_RECEIVED:
                        textResponderMessage.setText(R.string.responder_message_received);
                        break;
                    case SOSReport.STATUS_RESPONDING:
                        textResponderMessage.setText(R.string.responder_message_responding);
                        break;
                    case SOSReport.STATUS_RESOLVED:
                        textResponderMessage.setText(R.string.responder_message_resolved);
                        break;
                    default:
                        textResponderMessage.setText(R.string.responder_message_default);
                        break;
                }
            }

            // Update cancel button visibility - only when needed
            boolean showCancelButton = !report.getStatus().equals(SOSReport.STATUS_RESOLVED);
            if ((buttonCancel.getVisibility() == View.VISIBLE) != showCancelButton) {
                buttonCancel.setVisibility(showCancelButton ? View.VISIBLE : View.GONE);
            }

            // Update close button text
            buttonClose.setText(report.getStatus().equals(SOSReport.STATUS_RESOLVED) ?
                    R.string.close : R.string.minimize);
        };

        // Ensure UI updates happen on the main thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            uiUpdate.run();
        } else {
            new Handler(Looper.getMainLooper()).post(uiUpdate);
        }
    }

    /**
     * Update the status display
     */
    private void updateStatus(String status) {
        int colorResId;
        int descriptionResId;

        switch (status) {
            case SOSReport.STATUS_PENDING:
                colorResId = R.color.warning_yellow;
                descriptionResId = R.string.status_pending_description;
                textStatus.setText(R.string.status_pending);
                break;
            case SOSReport.STATUS_RECEIVED:
                colorResId = R.color.info_blue;
                descriptionResId = R.string.status_received_description;
                textStatus.setText(R.string.status_received);
                break;
            case SOSReport.STATUS_RESPONDING:
                colorResId = R.color.green_success;
                descriptionResId = R.string.status_responding_description;
                textStatus.setText(R.string.status_responding);
                break;
            case SOSReport.STATUS_RESOLVED:
                colorResId = R.color.green_success;
                descriptionResId = R.string.status_resolved_description;
                textStatus.setText(R.string.status_resolved);
                break;
            case SOSReport.STATUS_CANCELED:
                colorResId = R.color.red_error;
                descriptionResId = R.string.status_canceled_description;
                textStatus.setText(R.string.status_canceled);
                break;
            default:
                colorResId = R.color.warning_yellow;
                descriptionResId = R.string.status_unknown_description;
                textStatus.setText(R.string.status_unknown);
                break;
        }

        textStatus.setTextColor(ContextCompat.getColor(getContext(), colorResId));
        textStatusDescription.setText(descriptionResId);
    }

    /**
     * Cancel the emergency (mark as canceled)
     */
    private void cancelEmergency() {
        // Show confirmation dialog
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        builder.setTitle(R.string.cancel_emergency_title)
                .setMessage(R.string.cancel_emergency_message)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    // Proceed with cancellation using direct Firestore operations
                    performCancellation();
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    /**
     * Directly perform cancellation using Firestore operations
     * This bypasses the SOSRepository and SOSProcessingService for better reliability
     */
    private void performCancellation() {
        if (reportId == null || reportId.isEmpty()) return;

        progressBar.setVisibility(View.VISIBLE);

        try {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            DocumentReference reportRef = db.collection("sos_reports").document(reportId);

            // Prepare a minimal update that just changes the status
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", SOSReport.STATUS_CANCELED);
            updates.put("statusUpdatedAt", new Date());

            // Update the document
            reportRef.update(updates)
                    .addOnSuccessListener(aVoid -> {
                        // Success!
                        Log.d(TAG, "Successfully canceled SOS report");

                        // Update UI
                        progressBar.setVisibility(View.GONE);

                        // Update local report state
                        if (report != null) {
                            report.setStatus(SOSReport.STATUS_CANCELED);
                        }

                        // Update UI to reflect cancellation
                        updateStatus(SOSReport.STATUS_CANCELED);
                        buttonCancel.setVisibility(View.GONE);

                        // Change close button text
                        buttonClose.setText(R.string.close);

                        // Clear active SOS state
                        clearSOSState();

                        // Show success message
                        ToastUtil.showShort(getContext(),
                                getContext().getString(R.string.emergency_cancelled));

                        // Notify listener
                        if (listener != null) {
                            listener.onStatusChanged(reportId, SOSReport.STATUS_CANCELED);
                        }

                        // Also clean up the RTDB
                        try {
                            FirebaseDatabase.getInstance()
                                    .getReference("active_emergencies")
                                    .child(reportId)
                                    .removeValue();
                        } catch (Exception e) {
                            Log.e(TAG, "Error removing from active emergencies", e);
                        }

                        // Auto-dismiss dialog after 2 seconds
                        uiHandler.postDelayed(() -> {
                            dismiss();
                        }, 2000);
                    })
                    .addOnFailureListener(e -> {
                        // Handle failure
                        Log.e(TAG, "Failed to cancel SOS report", e);
                        progressBar.setVisibility(View.GONE);
                        ToastUtil.showShort(getContext(),
                                "Could not cancel emergency: " + e.getMessage());
                    });
        } catch (Exception e) {
            Log.e(TAG, "Exception in performCancellation", e);
            progressBar.setVisibility(View.GONE);
            ToastUtil.showShort(getContext(), "An error occurred. Please try again.");
        }
    }

    /**
     * Clear the active SOS state when resolved or cancelled
     */
    private void clearSOSState() {
        // Clear from preferences
        prefs.edit()
                .remove(KEY_ACTIVE_SOS)
                .remove(KEY_SOS_ID)
                .remove(KEY_MINIMIZED)
                .apply();

        // Also clear from the main shared preferences to ensure consistency
        Context context = getContext();
        if (context != null) {
            SharedPreferences mainPrefs = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE);
            mainPrefs.edit()
                    .remove("active_sos_report_id")
                    .remove("sos_dialog_minimized")
                    .putBoolean("has_active_sos", false)
                    .apply();
        }

        Log.d(TAG, "Cleared all SOS state data from dialog");
    }

    /**
     * Handle errors
     */
    private void handleError(String errorMessage) {
        Toast.makeText(getContext(), errorMessage, Toast.LENGTH_SHORT).show();
        if (listener != null) {
            listener.onError(errorMessage);
        }
        dismiss();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeStatusListener();
    }

    @Override
    public void dismiss() {
        // Prevent multiple dismissals
        if (isClosed) return;

        isClosed = true;
        removeStatusListener();

        // Check if we're dismissing with a final status
        boolean isFinalStatus = (report != null &&
                (SOSReport.STATUS_RESOLVED.equals(report.getStatus()) ||
                        SOSReport.STATUS_CANCELED.equals(report.getStatus())));

        if (isFinalStatus) {
            // Clear the state if resolved or cancelled
            clearSOSState();
            Log.d(TAG, "Cleared SOS state on final status: " +
                    (report != null ? report.getStatus() : "unknown"));
        } else {
            // Otherwise mark as minimized
            prefs.edit()
                    .putBoolean(KEY_MINIMIZED, true)
                    .apply();
            Log.d(TAG, "Marked SOS dialog as minimized");
        }

        // Use handler to prevent window focus ANRs
        if (listener != null && report != null) {
            final String status = report.getStatus();
            final String id = reportId;

            uiHandler.postDelayed(() -> {
                try {
                    if (listener != null) {
                        listener.onDismissed(id, status);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in dismiss callback", e);
                }
            }, 100);
        }

        super.dismiss();
    }

    /**
     * Remove Firebase listener when dialog is closed
     */
    private void removeStatusListener() {
        if (reportListener != null) {
            reportListener.remove();
            reportListener = null;
        }
    }

    /**
     * Interface for dialog events
     */
    public interface SOSStatusDialogListener {
        /**
         * Called when the status of the SOS report changes
         * @param reportId Report ID
         * @param newStatus New status
         */
        void onStatusChanged(String reportId, String newStatus);

        /**
         * Called when the dialog is dismissed
         * @param reportId Report ID
         * @param currentStatus Current status when dismissed
         */
        void onDismissed(String reportId, String currentStatus);

        /**
         * Called when an error occurs
         * @param errorMessage Error message
         */
        void onError(String errorMessage);
    }
}