package com.rescuereach.citizen;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.messaging.FirebaseMessaging;
import com.rescuereach.R;
import com.rescuereach.data.model.User;
import com.rescuereach.data.repository.OnCompleteListener;
import com.rescuereach.data.repository.RepositoryProvider;
import com.rescuereach.data.repository.UserRepository;
import com.rescuereach.service.auth.AuthService;
import com.rescuereach.service.auth.AuthServiceProvider;
import com.rescuereach.service.auth.SMSRetrieverHelper;
import com.rescuereach.service.auth.UserSessionManager;
import com.rescuereach.ui.common.OTPInputView;

import java.util.regex.Pattern;

public class PhoneAuthActivity extends AppCompatActivity implements OTPInputView.OTPCompletionListener {
    private static final String TAG = "PhoneAuthActivity";

    // Constants
    private static final int NETWORK_TIMEOUT_MS = 30000; // 30 seconds timeout for network operations

    // UI Components
    private EditText phoneEditText;
    private Button sendCodeButton;
    private Button clearButton;
    private Button verifyCodeButton;
    private ImageButton backButton;
    private TextView phoneDisplayText;
    private TextView resendCodeText;
    private ViewFlipper viewFlipper;
    private ProgressBar progressBar;
    private OTPInputView otpInputView;

    // Services
    private AuthService authService;
    private UserRepository userRepository;
    private UserSessionManager sessionManager;
    private SMSRetrieverHelper smsRetrieverHelper;

    // State variables
    private String verificationId;
    private String phoneNumber;
    private boolean isRequestInProgress = false;
    private Handler networkTimeoutHandler;
    private Runnable networkTimeoutRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_auth);

        // Initialize handler for network timeouts
        networkTimeoutHandler = new Handler(Looper.getMainLooper());

        // Initialize services
        authService = AuthServiceProvider.getInstance().getAuthService();
        userRepository = RepositoryProvider.getInstance().getUserRepository();
        sessionManager = UserSessionManager.getInstance(this);
        smsRetrieverHelper = new SMSRetrieverHelper(this);

        // Check if already authenticated
        if (authService.isLoggedIn() && sessionManager.getSavedPhoneNumber() != null) {
            navigateToMain();
            return;
        }

        // Initialize views
        initializeViews();

        // Set up listeners
        setupListeners();

        // Set up SMS retriever
        setupSMSRetriever();
    }

    private void initializeViews() {
        phoneEditText = findViewById(R.id.edit_phone);
        sendCodeButton = findViewById(R.id.button_send_code);
        clearButton = findViewById(R.id.button_clear);
        verifyCodeButton = findViewById(R.id.button_verify_code);
        backButton = findViewById(R.id.button_back);
        phoneDisplayText = findViewById(R.id.text_phone_display);
        resendCodeText = findViewById(R.id.text_resend_code);
        viewFlipper = findViewById(R.id.viewFlipper);
        progressBar = findViewById(R.id.progress_bar);
        otpInputView = findViewById(R.id.otp_input_view);

        // Ensure ViewFlipper is showing first view on start
        viewFlipper.setDisplayedChild(0);
    }

    private void setupListeners() {
        otpInputView.setOTPCompletionListener(this);

        sendCodeButton.setOnClickListener(v -> {
            if (!isRequestInProgress) {
                sendVerificationCode();
            }
        });

        clearButton.setOnClickListener(v -> {
            resetAuthenticationState();
            // Give user feedback
            Toast.makeText(this, "Form cleared", Toast.LENGTH_SHORT).show();
        });

        verifyCodeButton.setOnClickListener(v -> {
            if (!isRequestInProgress) {
                verifyCode();
            }
        });

        backButton.setOnClickListener(v -> {
            Animation slideInLeft = AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left);
            viewFlipper.setInAnimation(slideInLeft);
            resetAuthenticationState();
            viewFlipper.setDisplayedChild(0);
        });

        resendCodeText.setOnClickListener(v -> {
            if (!isRequestInProgress) {
                resendVerificationCode();
            } else {
                Toast.makeText(this, "Please wait, a request is already in progress", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupSMSRetriever() {
        smsRetrieverHelper.setSMSRetrievedListener(new SMSRetrieverHelper.SMSRetrievedListener() {
            @Override
            public void onSMSRetrieved(String otp) {
                Log.d(TAG, "OTP retrieved: " + otp);
                if (otpInputView != null && !isFinishing() && !isDestroyed()) {
                    runOnUiThread(() -> {
                        otpInputView.setOTP(otp);
                        // Automatically verify after small delay to give UI time to update
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            if (!isFinishing() && !isDestroyed()) {
                                verifyCode();
                            }
                        }, 500);
                    });
                }
            }

            @Override
            public void onSMSRetrievalFailed(Exception e) {
                Log.e(TAG, "SMS retrieval failed", e);
                // Silent failure - don't show error to user
            }
        });
    }

    @Override
    public void onOTPCompleted(String otp) {
        // OTP input complete, enable verify button
        if (!isDestroyed() && !isFinishing()) {
            verifyCodeButton.setEnabled(true);
        }
    }

    private void sendVerificationCode() {
        phoneNumber = phoneEditText.getText().toString().trim();

        if (TextUtils.isEmpty(phoneNumber)) {
            phoneEditText.setError("Phone number is required");
            return;
        }

        // Validate Indian phone number
        if (!isValidIndianPhoneNumber(phoneNumber)) {
            phoneEditText.setError("Please enter a valid 10-digit mobile number");
            return;
        }

        // Format phone number with country code if needed
        if (!phoneNumber.startsWith("+")) {
            phoneNumber = "+91" + phoneNumber; // Default to India country code
        }

        isRequestInProgress = true;
        showLoading(true);

        // Set a timeout for the network operation
        startNetworkTimeout(() -> {
            if (isRequestInProgress) {
                isRequestInProgress = false;
                showLoading(false);
                if (!isFinishing() && !isDestroyed()) {
                    showErrorDialog("Verification request timed out. Please check your internet connection and try again.");
                }
            }
        });

        authService.startPhoneVerification(phoneNumber, this, new AuthService.PhoneVerificationCallback() {
            @Override
            public void onCodeSent(String vId) {
                cancelNetworkTimeout();
                isRequestInProgress = false;
                showLoading(false);

                if (isFinishing() || isDestroyed()) return;

                verificationId = vId;
                phoneDisplayText.setText("Code sent to " + phoneNumber);

                // Show OTP verification view with animation
                Animation slideInRight = AnimationUtils.loadAnimation(PhoneAuthActivity.this, R.anim.slide_in_right);
                viewFlipper.setInAnimation(slideInRight);
                viewFlipper.setDisplayedChild(1);

                // Start SMS retriever to automatically capture the code
                smsRetrieverHelper.startSMSRetriever();

                Toast.makeText(PhoneAuthActivity.this, "Verification code sent", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onVerificationCompleted(PhoneAuthCredential credential) {
                cancelNetworkTimeout();
                isRequestInProgress = false;

                if (isFinishing() || isDestroyed()) return;

                Log.d(TAG, "onVerificationCompleted: Auto-verification successful");
                // Auto-verification completed, handle user creation/login
                showLoading(false);
                handlePhoneAuthSuccess(phoneNumber);

                // Subscribe to appropriate topics for push notifications
                subscribeToTopics();
            }

            @Override
            public void onVerificationFailed(Exception e) {
                cancelNetworkTimeout();
                isRequestInProgress = false;
                showLoading(false);

                if (isFinishing() || isDestroyed()) return;

                Log.e(TAG, "Phone verification failed", e);

                // Handle rate limiting errors specially
                if (e instanceof FirebaseTooManyRequestsException ||
                        (e.getMessage() != null && e.getMessage().contains("quota"))) {
                    showRateLimitErrorDialog();
                    return;
                }

                // Handle other errors
                String errorMessage;
                if (e instanceof FirebaseAuthInvalidCredentialsException) {
                    errorMessage = "Invalid phone number format. Please check and try again.";
                } else if (e instanceof FirebaseException && e.getMessage() != null) {
                    if (e.getMessage().contains("invalid format")) {
                        errorMessage = "The phone number format is invalid";
                    } else if (e.getMessage().contains("network")) {
                        errorMessage = "Network error. Check your connection";
                    } else {
                        errorMessage = "Verification failed: " + e.getMessage();
                    }
                } else {
                    errorMessage = "Verification failed. Please try again later.";
                }

                Toast.makeText(PhoneAuthActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showRateLimitErrorDialog() {
        if (isFinishing() || isDestroyed()) return;

        new AlertDialog.Builder(this)
                .setTitle("Too Many Attempts")
                .setMessage("You've made too many verification requests. Please wait at least 15 minutes before trying again.")
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    private void showErrorDialog(String message) {
        if (isFinishing() || isDestroyed()) return;

        new AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .setCancelable(true)
                .show();
    }

    private boolean isValidIndianPhoneNumber(String phoneNumber) {
        if (TextUtils.isEmpty(phoneNumber)) {
            return false;
        }

        // Remove any non-digit characters
        String cleaned = phoneNumber.replaceAll("[^\\d+]", "");

        // Valid formats: +91XXXXXXXXXX or just XXXXXXXXXX (10 digits starting with 6-9)
        if (cleaned.startsWith("+91")) {
            return cleaned.length() == 13 && Pattern.matches("^\\+91[6-9]\\d{9}$", cleaned);
        } else {
            return cleaned.length() == 10 && Pattern.matches("^[6-9]\\d{9}$", cleaned);
        }
    }

    private void resendVerificationCode() {
        if (TextUtils.isEmpty(phoneNumber)) {
            viewFlipper.setDisplayedChild(0);
            return;
        }

        isRequestInProgress = true;
        showLoading(true);

        // Set a timeout for the network operation
        startNetworkTimeout(() -> {
            if (isRequestInProgress) {
                isRequestInProgress = false;
                showLoading(false);
                if (!isFinishing() && !isDestroyed()) {
                    showErrorDialog("Resend request timed out. Please check your internet connection and try again.");
                }
            }
        });

        authService.startPhoneVerification(phoneNumber, this, new AuthService.PhoneVerificationCallback() {
            @Override
            public void onCodeSent(String vId) {
                cancelNetworkTimeout();
                isRequestInProgress = false;
                showLoading(false);

                if (isFinishing() || isDestroyed()) return;

                verificationId = vId;

                // Clear the input and start SMS retriever again
                otpInputView.clearOTP();
                smsRetrieverHelper.startSMSRetriever();

                Toast.makeText(PhoneAuthActivity.this, "Verification code resent", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onVerificationCompleted(PhoneAuthCredential credential) {
                cancelNetworkTimeout();
                isRequestInProgress = false;
                showLoading(false);

                if (isFinishing() || isDestroyed()) return;

                handlePhoneAuthSuccess(phoneNumber);
            }

            @Override
            public void onVerificationFailed(Exception e) {
                cancelNetworkTimeout();
                isRequestInProgress = false;
                showLoading(false);

                if (isFinishing() || isDestroyed()) return;

                Log.e(TAG, "Phone verification failed on resend", e);

                // Handle rate limiting errors specially
                if (e instanceof FirebaseTooManyRequestsException ||
                        (e.getMessage() != null && e.getMessage().contains("quota"))) {
                    showRateLimitErrorDialog();
                    return;
                }

                // Handle other errors
                String errorMessage = "Resend failed: " + (e.getMessage() != null ? e.getMessage() : "Unknown error");
                Toast.makeText(PhoneAuthActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void verifyCode() {
        String code = otpInputView.getOTP();

        if (TextUtils.isEmpty(code) || code.length() < 6) {
            Toast.makeText(this, "Please enter the complete 6-digit code", Toast.LENGTH_SHORT).show();
            return;
        }

        isRequestInProgress = true;
        showLoading(true);

        // Set a timeout for the network operation
        startNetworkTimeout(() -> {
            if (isRequestInProgress) {
                isRequestInProgress = false;
                showLoading(false);
                if (!isFinishing() && !isDestroyed()) {
                    showErrorDialog("Verification request timed out. Please check your internet connection and try again.");
                }
            }
        });

        authService.verifyPhoneWithCode(verificationId, code, new AuthService.AuthCallback() {
            @Override
            public void onSuccess() {
                cancelNetworkTimeout();
                isRequestInProgress = false;

                if (isFinishing() || isDestroyed()) return;

                handlePhoneAuthSuccess(phoneNumber);
                subscribeToTopics();
            }

            @Override
            public void onError(Exception e) {
                cancelNetworkTimeout();
                isRequestInProgress = false;
                showLoading(false);

                if (isFinishing() || isDestroyed()) return;

                Log.e(TAG, "Code verification failed", e);

                String errorMessage;
                if (e instanceof FirebaseAuthInvalidCredentialsException) {
                    errorMessage = "Invalid verification code. Please try again";
                    otpInputView.clearOTP();
                } else if (e.getMessage() != null && e.getMessage().contains("expired")) {
                    errorMessage = "Code expired. Please request a new code";
                } else {
                    errorMessage = "Verification failed. Please try again.";
                }

                Toast.makeText(PhoneAuthActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void startNetworkTimeout(Runnable timeoutAction) {
        cancelNetworkTimeout(); // Cancel any existing timeout

        networkTimeoutRunnable = timeoutAction;
        networkTimeoutHandler.postDelayed(networkTimeoutRunnable, NETWORK_TIMEOUT_MS);
    }

    private void cancelNetworkTimeout() {
        if (networkTimeoutRunnable != null) {
            networkTimeoutHandler.removeCallbacks(networkTimeoutRunnable);
            networkTimeoutRunnable = null;
        }
    }

    private void handlePhoneAuthSuccess(final String phoneNumber) {
        isRequestInProgress = true;
        showLoading(true);

        // Set a timeout for the database operation
        startNetworkTimeout(() -> {
            if (isRequestInProgress) {
                isRequestInProgress = false;
                showLoading(false);
                if (!isFinishing() && !isDestroyed()) {
                    // Even if database check times out, we can still proceed with creating a new user
                    createNewUser(phoneNumber);
                }
            }
        });

        // Check if user exists in database
        userRepository.getUserByPhoneNumber(phoneNumber, new UserRepository.OnUserFetchedListener() {
            @Override
            public void onSuccess(User user) {
                cancelNetworkTimeout();
                isRequestInProgress = false;
                showLoading(false);

                if (isFinishing() || isDestroyed()) return;

                // User exists, save to session
                sessionManager.saveUserPhoneNumber(phoneNumber);
                navigateToMain();
            }

            @Override
            public void onError(Exception e) {
                cancelNetworkTimeout();
                isRequestInProgress = false;

                if (isFinishing() || isDestroyed()) return;

                // Handle specific database errors
                if (e != null && e.getMessage() != null && e.getMessage().contains("User not found")) {
                    // User doesn't exist, create new user
                    createNewUser(phoneNumber);
                } else {
                    // Other database error
                    showLoading(false);
                    Log.e(TAG, "Database error during user check", e);
                    Toast.makeText(PhoneAuthActivity.this,
                            "Database error: " + (e != null ? e.getMessage() : "Unknown error"),
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void createNewUser(String phoneNumber) {
        isRequestInProgress = true;
        showLoading(true);

        // Set a timeout for the database operation
        startNetworkTimeout(() -> {
            if (isRequestInProgress) {
                isRequestInProgress = false;
                showLoading(false);
                if (!isFinishing() && !isDestroyed()) {
                    showErrorDialog("Database operation timed out. Please try again later.");
                }
            }
        });

        sessionManager.createNewUser(phoneNumber, new OnCompleteListener() {
            @Override
            public void onSuccess() {
                cancelNetworkTimeout();
                isRequestInProgress = false;
                showLoading(false);

                if (isFinishing() || isDestroyed()) return;

                navigateToMain();
            }

            @Override
            public void onError(Exception e) {
                cancelNetworkTimeout();
                isRequestInProgress = false;
                showLoading(false);

                if (isFinishing() || isDestroyed()) return;

                Log.e(TAG, "Failed to create user", e);

                String errorMessage = "Failed to create user: ";
                if (e != null && e.getMessage() != null) {
                    if (e.getMessage().contains("network")) {
                        errorMessage += "Network error. Please check your connection";
                    } else if (e.getMessage().contains("permission")) {
                        errorMessage += "Permission denied. Please contact support";
                    } else {
                        errorMessage += e.getMessage();
                    }
                } else {
                    errorMessage += "Unknown error";
                }

                Toast.makeText(PhoneAuthActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void navigateToMain() {
        // Check if user profile is complete
        if (sessionManager.isProfileComplete()) {
            // If profile is complete, go to main screen
            startActivity(new Intent(this, CitizenMainActivity.class));
        } else {
            // If profile is not complete, go to profile completion screen
            startActivity(new Intent(this, ProfileCompletionActivity.class));
        }
        finish();
    }

    // Subscribe to FCM topics for citizens
    private void subscribeToTopics() {
        FirebaseMessaging.getInstance().subscribeToTopic("citizens")
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Subscribed to 'citizens' topic");
                    } else {
                        Log.e(TAG, "Failed to subscribe to 'citizens' topic", task.getException());
                    }
                });
    }

    private void showLoading(boolean isLoading) {
        if (isFinishing() || isDestroyed()) return;

        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);

        // Disable UI interaction during loading
        sendCodeButton.setEnabled(!isLoading);
        clearButton.setEnabled(!isLoading);

        // Only enable verify button if OTP is complete
        if (otpInputView != null) {
            String currentOtp = otpInputView.getOTP();
            verifyCodeButton.setEnabled(!isLoading && currentOtp != null && currentOtp.length() == 6);
        } else {
            verifyCodeButton.setEnabled(!isLoading);
        }

        backButton.setEnabled(!isLoading);
        resendCodeText.setEnabled(!isLoading);
        phoneEditText.setEnabled(!isLoading);

        if (otpInputView != null) {
            otpInputView.setEnabled(!isLoading);
        }
    }

    private void resetAuthenticationState() {
        // Cancel any ongoing operations
        cancelNetworkTimeout();
        isRequestInProgress = false;

        // Reset input fields
        if (phoneEditText != null) {
            phoneEditText.setText("");
            phoneEditText.setError(null);
            phoneEditText.requestFocus(); // Set focus back to phone field
        }

        if (otpInputView != null) {
            otpInputView.clearOTP();
        }

        // Reset verification state
        verificationId = null;

        // Ensure UI is not in loading state
        showLoading(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Don't cancel operations on pause - they should continue in background
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Cancel any pending timeouts
        cancelNetworkTimeout();

        // Clean up SMS retriever
        if (smsRetrieverHelper != null) {
            smsRetrieverHelper.unregisterReceiver();
            smsRetrieverHelper = null;
        }
    }
}