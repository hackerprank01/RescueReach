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

    // Handler for main thread operations
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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

        // Initialize services safely
        try {
            RescueReachApplication app = (RescueReachApplication) context.getApplicationContext();
            this.notificationService = app.getNotificationService();
            this.sessionManager = UserSessionManager.getInstance(context);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing services", e);
            // Continue without services - core functionality will still work
        }
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

            // Keep screen on during countdown
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        try {
            // Initialize UI components
            initializeViews();

            // Configure based on emergency type
            configureForEmergencyType();

            // Set up click listeners
            setupClickListeners();

            // Start countdown
            startCountdown();
        } catch (Exception e) {
            Log.e(TAG, "Error during dialog setup", e);
            // Ensure dialog can be dismissed if there's an error
            setupFallbackDismissal();
        }
    }

    private void initializeViews() {
        try {
            rootLayout = findViewById(R.id.sos_dialog_root);
            imageEmergencyType = findViewById(R.id.image_emergency_type);
            textEmergencyType = findViewById(R.id.text_emergency_type);
            textCountdown = findViewById(R.id.text_countdown);
            progressCountdown = findViewById(R.id.progress_countdown);
            buttonConfirm = findViewById(R.id.button_confirm);

            // Set max progress for countdown
            if (progressCountdown != null) {
                progressCountdown.setMax(TOTAL_PROGRESS);
                progressCountdown.setProgress(TOTAL_PROGRESS);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views", e);
            throw e; // Re-throw to be caught by onCreate
        }
    }

    private void configureForEmergencyType() {
        try {
            if (imageEmergencyType == null || textEmergencyType == null) return;

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
        } catch (Exception e) {
            Log.e(TAG, "Error configuring emergency type", e);
            // Use default values if specific ones fail
            if (imageEmergencyType != null) {
                imageEmergencyType.setImageResource(R.drawable.ic_sos_emergency);
            }
            if (textEmergencyType != null) {
                textEmergencyType.setText(R.string.type_emergency);
            }
        }
    }

    private void setupClickListeners() {
        try {
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
        } catch (Exception e) {
            Log.e(TAG, "Error setting up click listeners", e);
            setupFallbackDismissal();
        }
    }

    private void setupFallbackDismissal() {
        // In case of errors, ensure dialog can still be dismissed
        try {
            MaterialButton button = findViewById(R.id.button_confirm);
            if (button != null) {
                button.setOnClickListener(v -> dismiss());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up fallback dismissal", e);
        }
    }

    private void startCountdown() {
        try {
            countDownTimer = new CountDownTimer(COUNTDOWN_SECONDS * 1000, COUNTDOWN_INTERVAL) {
                @Override
                public void onTick(long millisUntilFinished) {
                    if (isCancelled || isConfirmed) return;

                    // Run UI updates on main thread safely
                    mainHandler.post(() -> {
                        try {
                            // Update countdown text
                            if (textCountdown != null) {
                                int secondsRemaining = (int) Math.ceil(millisUntilFinished / 1000.0);
                                textCountdown.setText(String.valueOf(secondsRemaining));
                            }

                            // Update progress
                            if (progressCountdown != null) {
                                int progress = (int) (TOTAL_PROGRESS * millisUntilFinished / (COUNTDOWN_SECONDS * 1000));
                                progressCountdown.setProgress(progress);
                            }

                            // Add visual pulse effect when 2 seconds remaining
                            if (millisUntilFinished <= 2000 && millisUntilFinished > 1900) {
                                pulseCountdown();
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error updating countdown UI", e);
                        }
                    });
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
        } catch (Exception e) {
            Log.e(TAG, "Error starting countdown", e);

            // If countdown fails, immediately confirm SOS to ensure emergency is reported
            mainHandler.post(this::confirmSOS);
        }
    }

    private void pulseCountdown() {
        try {
            if (textCountdown != null) {
                Animation pulseAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.pulse);
                textCountdown.startAnimation(pulseAnimation);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying pulse animation", e);
        }
    }

    private void cancelSOS() {
        try {
            if (isCancelled) return; // Prevent double cancellation
            isCancelled = true;

            if (countDownTimer != null) {
                countDownTimer.cancel();
            }

            Log.d(TAG, "SOS cancelled by user");

            // Update notification tags to indicate user has interacted with SOS
            updateNotificationTags();

            // Use try-finally to ensure dismiss() is called even if listener throws an exception
            try {
                if (listener != null) {
                    listener.onSOSCancelled();
                }
            } finally {
                safeDismiss();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error cancelling SOS", e);
            safeDismiss();
        }
    }

    private void confirmSOS() {
        try {
            if (isConfirmed) return; // Prevent double confirmation
            isConfirmed = true;

            if (countDownTimer != null) {
                countDownTimer.cancel();
            }

            // Disable button to prevent further interaction
            if (buttonConfirm != null) {
                buttonConfirm.setEnabled(false);
            }

            // Show a brief visual feedback before closing dialog
            if (textCountdown != null) {
                textCountdown.setText("✓");
                try {
                    textCountdown.setTextColor(getContext().getResources().getColor(R.color.green_success, null));
                } catch (Exception e) {
                    // Fallback for older Android versions
                    textCountdown.setTextColor(getContext().getResources().getColor(R.color.green_success));
                }
            }

            if (progressCountdown != null) {
                try {
                    progressCountdown.setIndicatorColor(getContext().getResources().getColor(R.color.green_success, null));
                } catch (Exception e) {
                    // Fallback for older Android versions
                    progressCountdown.setIndicatorColor(getContext().getResources().getColor(R.color.green_success));
                }
                progressCountdown.setProgress(TOTAL_PROGRESS);
            }

            // Update notification tags to indicate emergency type preference
            updateNotificationTags();

            // Close dialog after brief delay
            mainHandler.postDelayed(() -> {
                try {
                    // Use try-finally to ensure dismiss() is called even if listener throws an exception
                    try {
                        if (listener != null) {
                            listener.onSOSConfirmed(emergencyType);
                        }
                    } finally {
                        safeDismiss();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in delayed confirmation", e);
                    safeDismiss();
                }
            }, 500);
        } catch (Exception e) {
            Log.e(TAG, "Error confirming SOS", e);

            // Even if UI updates fail, make sure the SOS is confirmed
            try {
                if (listener != null) {
                    listener.onSOSConfirmed(emergencyType);
                }
            } catch (Exception listenerError) {
                Log.e(TAG, "Error in SOS confirmation listener", listenerError);
            }

            safeDismiss();
        }
    }

    private void updateNotificationTags() {
        try {
            if (notificationService != null) {
                if (isConfirmed) {
                    notificationService.setEmergencyPreference(emergencyType);
                }
                notificationService.updateLastActive();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating notification tags", e);
        }
    }

    private void safeDismiss() {
        try {
            dismiss();
        } catch (Exception e) {
            Log.e(TAG, "Error dismissing dialog", e);
        }
    }

    @Override
    public void onDetachedFromWindow() {
        try {
            // Ensure countdown timer is canceled
            if (countDownTimer != null) {
                countDownTimer.cancel();
                countDownTimer = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during detach", e);
        }

        super.onDetachedFromWindow();
    }

    /**
     * Interface to communicate with the host fragment/activity
     */
    public interface SOSDialogListener {
        void onSOSConfirmed(String emergencyType);
        void onSOSCancelled();
    }
}