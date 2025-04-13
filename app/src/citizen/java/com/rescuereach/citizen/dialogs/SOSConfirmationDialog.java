package com.rescuereach.citizen.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.rescuereach.R;
import com.rescuereach.RescueReachApplication;
import com.rescuereach.service.auth.UserSessionManager;
import com.rescuereach.service.notification.NotificationService;

/**
 * Dialog for confirming SOS emergency alerts
 * Features a 5-second countdown after which the SOS is automatically sent
 * unless canceled by the user
 */
public class SOSConfirmationDialog extends Dialog {
    private static final String TAG = "SOSConfirmationDialog";

    // UI components
    private ConstraintLayout rootLayout;
    private ImageView imageEmergencyType;
    private TextView textEmergencyType;
    private TextView textCountdown;
    private CircularProgressIndicator progressCountdown;
    private MaterialButton buttonConfirm;

    // State
    private final String emergencyType;
    private final SOSDialogListener listener;
    private CountDownTimer countDownTimer;
    private boolean isConfirmed = false;
    private boolean isCancelled = false;

    // Services
    private NotificationService notificationService;
    private UserSessionManager sessionManager;

    // Constants
    private static final int COUNTDOWN_SECONDS = 5;
    private static final int COUNTDOWN_INTERVAL = 100; // ms
    private static final int TOTAL_PROGRESS = 100;

    /**
     * Create a new SOS confirmation dialog
     * @param context The context
     * @param emergencyType The type of emergency (POLICE, FIRE, MEDICAL)
     * @param listener Callback for dialog actions
     */
    public SOSConfirmationDialog(@NonNull Context context, String emergencyType,
                                 SOSDialogListener listener) {
        super(context, R.style.Theme_RescueReach_Dialog);
        this.emergencyType = emergencyType;
        this.listener = listener;

        // Initialize services
        this.notificationService = ((RescueReachApplication) context.getApplicationContext())
                .getNotificationService();
        this.sessionManager = UserSessionManager.getInstance(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_sos_confirmation);

        // Make dialog non-cancelable by back button or touch outside
        setCancelable(false);
        setCanceledOnTouchOutside(false);

        // Set dialog width to match parent with margins
        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

            // Set dim amount for background
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.dimAmount = 0.7f;
            getWindow().setAttributes(params);
        }

        // Initialize UI components
        initializeViews();

        // Configure based on emergency type
        configureForEmergencyType();

        // Set up click listeners
        setupClickListeners();

        // Start countdown
        startCountdown();
    }

    private void initializeViews() {
        rootLayout = findViewById(R.id.sos_dialog_root);
        imageEmergencyType = findViewById(R.id.image_emergency_type);
        textEmergencyType = findViewById(R.id.text_emergency_type);
        textCountdown = findViewById(R.id.text_countdown);
        progressCountdown = findViewById(R.id.progress_countdown);
        buttonConfirm = findViewById(R.id.button_confirm);

        // Set max progress for countdown
        progressCountdown.setMax(TOTAL_PROGRESS);
        progressCountdown.setProgress(TOTAL_PROGRESS);
    }

    private void configureForEmergencyType() {
        switch (emergencyType) {
            case "POLICE":
                imageEmergencyType.setImageResource(R.drawable.ic_police);
                textEmergencyType.setText(R.string.type_police);
                break;
            case "FIRE":
                imageEmergencyType.setImageResource(R.drawable.ic_fire);
                textEmergencyType.setText(R.string.type_fire);
                break;
            case "MEDICAL":
                imageEmergencyType.setImageResource(R.drawable.ic_medical);
                textEmergencyType.setText(R.string.type_medical);
                break;
            default:
                imageEmergencyType.setImageResource(R.drawable.ic_sos_emergency);
                textEmergencyType.setText(R.string.type_emergency);
                break;
        }
    }

    private void setupClickListeners() {
        if (rootLayout == null || buttonConfirm == null) {
            Log.e(TAG, "Views not initialized properly");
            return;
        }

        // Set root layout as tap-to-cancel area
        rootLayout.setOnClickListener(v -> {
            if (!isConfirmed && !isCancelled) {
                cancelSOS();
            }
        });

        // Setup confirm button that shouldn't trigger the root click
        buttonConfirm.setOnClickListener(v -> confirmSOS());

        // Prevent click propagation from confirm button to root layout
        buttonConfirm.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.performClick();
                return true;
            }
            return false;
        });
    }

    private void startCountdown() {
        countDownTimer = new CountDownTimer(COUNTDOWN_SECONDS * 1000, COUNTDOWN_INTERVAL) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (isCancelled || isConfirmed) return;

                // Update countdown text
                int secondsRemaining = (int) Math.ceil(millisUntilFinished / 1000.0);
                textCountdown.setText(String.valueOf(secondsRemaining));

                // Update progress
                int progress = (int) (TOTAL_PROGRESS * millisUntilFinished / (COUNTDOWN_SECONDS * 1000));
                progressCountdown.setProgress(progress);

                // Add visual pulse effect when 2 seconds remaining
                if (millisUntilFinished <= 2000 && millisUntilFinished > 1900) {
                    pulseCountdown();
                }
            }

            @Override
            public void onFinish() {
                // When countdown finishes and dialog is still active, confirm SOS
                if (!isCancelled && !isConfirmed) {
                    confirmSOS();
                }
            }
        };

        // Start the countdown
        countDownTimer.start();
    }

    private void pulseCountdown() {
        Animation pulseAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.pulse);
        textCountdown.startAnimation(pulseAnimation);
    }

    private void cancelSOS() {
        if (isCancelled) return; // Prevent double cancellation

        isCancelled = true;

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        Log.d(TAG, "SOS cancelled by user");

        // Update notification tags to indicate user has interacted with SOS
        if (notificationService != null) {
            notificationService.updateLastActive();
        }

        dismiss();

        if (listener != null) {
            listener.onSOSCancelled();
        }
    }

    private void confirmSOS() {
        if (isConfirmed) return; // Prevent double confirmation

        isConfirmed = true;

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        // Disable button to prevent further interaction
        buttonConfirm.setEnabled(false);

        // Show a brief visual feedback before closing dialog
        textCountdown.setText("✓");
        textCountdown.setTextColor(getContext().getResources().getColor(R.color.green_success, null));
        progressCountdown.setIndicatorColor(getContext().getResources().getColor(R.color.green_success, null));
        progressCountdown.setProgress(TOTAL_PROGRESS);

        // Update notification tags to indicate emergency type preference
        if (notificationService != null) {
            notificationService.setEmergencyPreference(emergencyType);
            notificationService.updateLastActive();
        }

        // Close dialog after brief delay
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            dismiss();

            if (listener != null) {
                listener.onSOSConfirmed(emergencyType);
            }
        }, 500);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        // Ensure countdown timer is canceled
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    /**
     * Interface to communicate with the host fragment/activity
     */
    public interface SOSDialogListener {
        void onSOSConfirmed(String emergencyType);
        void onSOSCancelled();
    }
}