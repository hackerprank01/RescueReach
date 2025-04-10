package com.rescuereach.citizen;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.content.pm.PackageManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;
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

import java.util.Date;
import java.util.regex.Pattern;

public class PhoneAuthActivity extends AppCompatActivity implements OTPInputView.OTPCompletionListener {
    private static final String TAG = "PhoneAuthActivity";

    // Constants
    private static final int NETWORK_TIMEOUT_MS = 30000; // 30 seconds timeout
    private static final int RESEND_COOLDOWN_MS = 60000; // 60 seconds cooldown for resend
    private static final int SMS_PERMISSION_REQUEST_CODE = 123;

    // UI Components
    private EditText phoneEditText;
    private Button sendCodeButton;
    private Button clearButton;
    private Button verifyCodeButton;
    private ImageButton backButton;
    private TextView phoneDisplayText;
    private TextView resendCodeText;
    private TextView countdownTimerText;
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
    private PhoneAuthProvider.ForceResendingToken resendToken;
    private boolean isRequestInProgress = false;
    private Handler networkTimeoutHandler;
    private Runnable networkTimeoutRunnable;
    private CountDownTimer resendCooldownTimer;
    private BroadcastReceiver smsBroadcastReceiver;

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
        countdownTimerText = findViewById(R.id.text_countdown_timer);
        viewFlipper = findViewById(R.id.viewFlipper);
        progressBar = findViewById(R.id.progress_bar);
        otpInputView = findViewById(R.id.otp_input_view);

        // Ensure ViewFlipper is showing first view on start
        viewFlipper.setDisplayedChild(0);

        // Initially disable the resend option
        resendCodeText.setEnabled(false);
        countdownTimerText.setVisibility(View.GONE);
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
            cancelResendCooldown();
            resetAuthenticationState();
            viewFlipper.setDisplayedChild(0);
        });

        resendCodeText.setOnClickListener(v -> {
            if (!isRequestInProgress && resendCodeText.isEnabled()) {
                resendVerificationCode();
            } else if (!resendCodeText.isEnabled()) {
                Toast.makeText(this, "Please wait before requesting a new code", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Please wait, a request is already in progress", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupSMSRetriever() {
        // Check and request SMS read permission for Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECEIVE_SMS},
                        SMS_PERMISSION_REQUEST_CODE);
            } else {
                registerSMSReceiver();
            }
        } else {
            registerSMSReceiver();
        }

        // Also try the Google SMS Retriever API as a fallback
        try {
            smsRetrieverHelper.setSMSRetrievedListener(new SMSRetrieverHelper.SMSRetrievedListener() {
                @Override
                public void onSMSRetrieved(String otp) {
                    Log.d(TAG, "OTP retrieved from SMS Retriever API: " + otp);
                    populateOTPAndVerify(otp);
                }

                @Override
                public void onSMSRetrievalFailed(Exception e) {
                    Log.e(TAG, "SMS Retriever API failed", e);
                    // Silent failure - user can still enter code manually
                }
            });
            smsRetrieverHelper.startSMSRetriever();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing SMS Retriever", e);
        }
    }

    private void registerSMSReceiver() {
        // Unregister any existing receiver
        if (smsBroadcastReceiver != null) {
            try {
                unregisterReceiver(smsBroadcastReceiver);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering existing SMS receiver", e);
            }
        }

        // Create a new broadcast receiver for SMS
        smsBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction() != null &&
                        intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED")) {

                    Bundle bundle = intent.getExtras();
                    if (bundle != null) {
                        // Extract SMS messages
                        Object[] pdus = (Object[]) bundle.get("pdus");
                        if (pdus != null) {
                            for (Object pdu : pdus) {
                                android.telephony.SmsMessage smsMessage;
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    String format = bundle.getString("format");
                                    smsMessage = android.telephony.SmsMessage.createFromPdu((byte[]) pdu, format);
                                } else {
                                    smsMessage = android.telephony.SmsMessage.createFromPdu((byte[]) pdu);
                                }

                                String messageBody = smsMessage.getMessageBody();
                                Log.d(TAG, "SMS received: " + messageBody);

                                // Extract OTP from the message
                                String otp = extractOTPFromMessage(messageBody);
                                if (otp != null) {
                                    Log.d(TAG, "OTP extracted from SMS: " + otp);
                                    populateOTPAndVerify(otp);
                                }
                            }
                        }
                    }
                }
            }
        };

        // Register the receiver
        IntentFilter filter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
        filter.setPriority(999); // High priority
        registerReceiver(smsBroadcastReceiver, filter);
        Log.d(TAG, "SMS broadcast receiver registered");
    }

    private String extractOTPFromMessage(String message) {
        if (message == null || message.isEmpty()) {
            return null;
        }

        // Try multiple patterns to find the OTP
        // Pattern 1: Look for 6 consecutive digits
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d{6})");
        java.util.regex.Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Pattern 2: Look for "code" or "otp" followed by 6 digits
        pattern = java.util.regex.Pattern.compile("(?i)(?:verification|code|otp)[^0-9]*([0-9]{6})");
        matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    private void populateOTPAndVerify(String otp) {
        if (otpInputView != null && !isFinishing() && !isDestroyed()) {
            runOnUiThread(() -> {
                otpInputView.setOTP(otp);
                // Give UI time to update before verifying
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        verifyCode();
                    }
                }, 800);
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, register the receiver
                registerSMSReceiver();
            } else {
                // Permission denied, continue without SMS auto-reading
                Toast.makeText(this, "SMS auto-reading permission denied. Please enter the code manually.",
                        Toast.LENGTH_SHORT).show();
            }
        }
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
            public void onCodeSent(String vId, PhoneAuthProvider.ForceResendingToken token) {
                cancelNetworkTimeout();
                isRequestInProgress = false;
                showLoading(false);

                smsRetrieverHelper.unregisterReceiver(); // Unregister any existing receiver
                smsRetrieverHelper.startSMSRetriever(); // Start a new retriever

                if (isFinishing() || isDestroyed()) return;

                verificationId = vId;
                resendToken = token;
                phoneDisplayText.setText("Code sent to " + phoneNumber);

                // Show OTP verification view with animation
                Animation slideInRight = AnimationUtils.loadAnimation(PhoneAuthActivity.this, R.anim.slide_in_right);
                viewFlipper.setInAnimation(slideInRight);
                viewFlipper.setDisplayedChild(1);

                // Start countdown for resend button
                startResendCooldown();

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

    private void startResendCooldown() {
        // Cancel any existing timer
        cancelResendCooldown();

        // Disable resend button and show countdown
        resendCodeText.setEnabled(false);
        countdownTimerText.setVisibility(View.VISIBLE);

        // Create and start new countdown timer
        resendCooldownTimer = new CountDownTimer(RESEND_COOLDOWN_MS, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000);
                countdownTimerText.setText("Resend available in " + seconds + "s");
            }

            @Override
            public void onFinish() {
                // Enable resend button and hide countdown
                resendCodeText.setEnabled(true);
                countdownTimerText.setVisibility(View.GONE);
                resendCooldownTimer = null;
            }
        }.start();
    }

    private void cancelResendCooldown() {
        if (resendCooldownTimer != null) {
            resendCooldownTimer.cancel();
            resendCooldownTimer = null;
        }

        // Add null checks before accessing UI elements
        if (resendCodeText != null) {
            resendCodeText.setEnabled(true);
        }
        if (countdownTimerText != null) {
            countdownTimerText.setVisibility(View.GONE);
        }
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

        if (resendToken == null) {
            // If we don't have a resend token, just go back to phone input
            Toast.makeText(this, "Unable to resend code. Please try again.", Toast.LENGTH_SHORT).show();
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

        authService.resendVerificationCode(phoneNumber, resendToken, this, new AuthService.PhoneVerificationCallback() {
            @Override
            public void onCodeSent(String vId, PhoneAuthProvider.ForceResendingToken token) {
                cancelNetworkTimeout();
                isRequestInProgress = false;
                showLoading(false);

                if (isFinishing() || isDestroyed()) return;

                verificationId = vId;
                resendToken = token;

                // Clear the input and start SMS retriever again
                otpInputView.clearOTP();
                smsRetrieverHelper.startSMSRetriever();

                // Start new cooldown timer
                startResendCooldown();

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

                // If user profile data exists in database but not in shared prefs,
                // update shared prefs with this data
                if (user.getFullName() != null) {
                    sessionManager.saveUserProfileData(
                            user.getFirstName(), // first name
                            user.getLastName(), // last name
                            user.getDateOfBirth() != null ? user.getDateOfBirth() : new Date(), // date of birth
                            user.getState() != null ? user.getState() : "", // state, or "" as default
                            user.getEmergencyContact() != null ? user.getEmergencyContact() : "", // emergency contact
                            user.isVolunteer() // isVolunteer flag
                    );
                }

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

        if (progressBar != null) {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }

        // Disable UI interaction during loading with null checks
        if (sendCodeButton != null) {
            sendCodeButton.setEnabled(!isLoading);
        }
        if (clearButton != null) {
            clearButton.setEnabled(!isLoading);
        }

        // Only enable verify button if OTP is complete
        if (verifyCodeButton != null) {
            if (otpInputView != null) {
                String currentOtp = otpInputView.getOTP();
                verifyCodeButton.setEnabled(!isLoading && currentOtp != null && currentOtp.length() == 6);
            } else {
                verifyCodeButton.setEnabled(!isLoading);
            }
        }

        if (backButton != null) {
            backButton.setEnabled(!isLoading);
        }

        // Don't change resendCodeText enabled state here - it's managed by the cooldown timer

        if (phoneEditText != null) {
            phoneEditText.setEnabled(!isLoading);
        }

        if (otpInputView != null) {
            otpInputView.setEnabled(!isLoading);
        }
    }

    private void resetAuthenticationState() {
        // Cancel any ongoing operations
        cancelNetworkTimeout();
        cancelResendCooldown();
        isRequestInProgress = false;

        // Reset input fields with null checks
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
        resendToken = null;

        // Ensure UI is not in loading state
        showLoading(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Cancel any pending timeouts
        cancelNetworkTimeout();
        cancelResendCooldown();

        // Unregister SMS receivers
        if (smsBroadcastReceiver != null) {
            try {
                unregisterReceiver(smsBroadcastReceiver);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering SMS receiver", e);
            }
            smsBroadcastReceiver = null;
        }

        if (smsRetrieverHelper != null) {
            smsRetrieverHelper.unregisterReceiver();
            smsRetrieverHelper = null;
        }
    }
}