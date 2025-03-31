package com.rescuereach.citizen;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.PhoneAuthCredential;
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
    private static final long SMS_COOLDOWN_MS = 60000; // 60 seconds cooldown
    private static final String PREF_NAME = "phone_auth_prefs";
    private static final String PREF_LAST_SMS_REQUEST_TIME = "last_sms_request_time";
    private static final String PREF_LAST_PHONE_NUMBER = "last_phone_number";
    private static final int MAX_ATTEMPTS_PER_NUMBER = 3;
    private static final String PREF_ATTEMPT_COUNT = "attempt_count";

    private EditText phoneEditText;
    private Button sendCodeButton;
    private Button verifyCodeButton;
    private ImageButton backButton;
    private TextView phoneDisplayText;
    private TextView resendCodeText;
    private ViewFlipper viewFlipper;
    private ProgressBar progressBar;
    private OTPInputView otpInputView;
    private TextView countdownText;

    private AuthService authService;
    private UserRepository userRepository;
    private UserSessionManager sessionManager;
    private SMSRetrieverHelper smsRetrieverHelper;
    private SharedPreferences preferences;
    private CountDownTimer countDownTimer;

    private String verificationId;
    private String phoneNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_auth);

        // Initialize preferences
        preferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        // Initialize services
        authService = AuthServiceProvider.getInstance().getAuthService();
        userRepository = RepositoryProvider.getInstance().getUserRepository();
        sessionManager = UserSessionManager.getInstance(this);
        smsRetrieverHelper = new SMSRetrieverHelper(this);

        // Initialize views
        phoneEditText = findViewById(R.id.edit_phone);
        sendCodeButton = findViewById(R.id.button_send_code);
        verifyCodeButton = findViewById(R.id.button_verify_code);
        backButton = findViewById(R.id.button_back);
        phoneDisplayText = findViewById(R.id.text_phone_display);
        resendCodeText = findViewById(R.id.text_resend_code);
        viewFlipper = findViewById(R.id.viewFlipper);
        progressBar = findViewById(R.id.progress_bar);
        otpInputView = findViewById(R.id.otp_input_view);

        // Add countdown text
        countdownText = findViewById(R.id.text_countdown);
        if (countdownText == null) {
            // If not in layout yet, add programmatically after the send button
            countdownText = new TextView(this);
            countdownText.setId(View.generateViewId());
            countdownText.setTextColor(getResources().getColor(android.R.color.holo_red_light));
            countdownText.setVisibility(View.GONE);

            // Find the parent layout containing the send button
            View parent = (View) sendCodeButton.getParent();
            if (parent instanceof ViewGroup) {
                ViewGroup parentLayout = (ViewGroup) parent;
                // Add the TextView after the button
                int index = parentLayout.indexOfChild(sendCodeButton);
                parentLayout.addView(countdownText, index + 1);
            }
        }

        // Set up listeners
        otpInputView.setOTPCompletionListener(this);

        // Set up SMS retriever
        setupSMSRetriever();

        // Set up button click listeners
        sendCodeButton.setOnClickListener(v -> {
            if (canRequestSmsVerification()) {
                sendVerificationCode();
            } else {
                Toast.makeText(this, "Please wait before requesting another code", Toast.LENGTH_SHORT).show();
            }
        });

        verifyCodeButton.setOnClickListener(v -> verifyCode());
        backButton.setOnClickListener(v -> viewFlipper.setDisplayedChild(0));
        resendCodeText.setOnClickListener(v -> {
            if (canRequestSmsVerification()) {
                resendVerificationCode();
            } else {
                long remainingSeconds = getRemainingCooldownTimeSeconds();
                Toast.makeText(this,
                        "Please wait " + remainingSeconds + " seconds before requesting another code",
                        Toast.LENGTH_SHORT).show();
            }
        });

        // Check if we're in a cooldown period
        checkAndShowCooldown();
    }

    private void checkAndShowCooldown() {
        if (!canRequestSmsVerification()) {
            startCountdownTimer(getRemainingCooldownTimeMillis());
        }
    }

    private boolean canRequestSmsVerification() {
        long lastRequestTime = preferences.getLong(PREF_LAST_SMS_REQUEST_TIME, 0);
        long currentTime = System.currentTimeMillis();
        return currentTime - lastRequestTime >= SMS_COOLDOWN_MS;
    }

    private long getRemainingCooldownTimeMillis() {
        long lastRequestTime = preferences.getLong(PREF_LAST_SMS_REQUEST_TIME, 0);
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - lastRequestTime;
        return Math.max(0, SMS_COOLDOWN_MS - elapsedTime);
    }

    private long getRemainingCooldownTimeSeconds() {
        return getRemainingCooldownTimeMillis() / 1000;
    }

    private void startCountdownTimer(long milliseconds) {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        countdownText.setVisibility(View.VISIBLE);
        sendCodeButton.setEnabled(false);

        countDownTimer = new CountDownTimer(milliseconds, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                countdownText.setText("Please wait " + (millisUntilFinished / 1000) + " seconds before requesting another code");
            }

            @Override
            public void onFinish() {
                countdownText.setVisibility(View.GONE);
                sendCodeButton.setEnabled(true);
            }
        }.start();
    }

    private void setupSMSRetriever() {
        smsRetrieverHelper.setSMSRetrievedListener(new SMSRetrieverHelper.SMSRetrievedListener() {
            @Override
            public void onSMSRetrieved(String otp) {
                Log.d(TAG, "OTP retrieved: " + otp);
                if (otpInputView != null) {
                    otpInputView.setOTP(otp);
                    // Automatically verify after small delay to give UI time to update
                    otpInputView.postDelayed(() -> verifyCode(), 300);
                }
            }

            @Override
            public void onSMSRetrievalFailed(Exception e) {
                Log.e(TAG, "SMS retrieval failed", e);
                Toast.makeText(PhoneAuthActivity.this,
                        "SMS auto-detection failed. Please enter the code manually.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onOTPCompleted(String otp) {
        // OTP input complete, enable verify button
        verifyCodeButton.setEnabled(true);
    }

    private void sendVerificationCode() {
        phoneNumber = phoneEditText.getText().toString().trim();

        if (!isValidPhoneNumber(phoneNumber)) {
            phoneEditText.setError("Please enter a valid 10-digit phone number");
            return;
        }

        // Format phone number with country code if needed
        if (!phoneNumber.startsWith("+")) {
            phoneNumber = "+91" + phoneNumber; // Default to India country code
        }

        // Update attempt count for this number
        String lastPhoneNumber = preferences.getString(PREF_LAST_PHONE_NUMBER, "");
        int attempts = 0;

        if (phoneNumber.equals(lastPhoneNumber)) {
            attempts = preferences.getInt(PREF_ATTEMPT_COUNT, 0) + 1;
        } else {
            // Reset for new number
            preferences.edit().putString(PREF_LAST_PHONE_NUMBER, phoneNumber).apply();
        }

        preferences.edit().putInt(PREF_ATTEMPT_COUNT, attempts).apply();

        // Check if we've exceeded max attempts for this number
        if (attempts >= MAX_ATTEMPTS_PER_NUMBER) {
            Toast.makeText(this,
                    "Too many attempts for this number. Please try again later or use a different number.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        showLoading(true);

        // Record the current time for cooldown
        preferences.edit().putLong(PREF_LAST_SMS_REQUEST_TIME, System.currentTimeMillis()).apply();

        authService.startPhoneVerification(phoneNumber, this, new AuthService.PhoneVerificationCallback() {
            @Override
            public void onCodeSent(String vId) {
                showLoading(false);
                verificationId = vId;
                phoneDisplayText.setText("Code sent to " + phoneNumber);
                viewFlipper.setDisplayedChild(1);

                // Start SMS retriever to automatically capture the code
                smsRetrieverHelper.startSMSRetriever();

                Toast.makeText(PhoneAuthActivity.this, "Verification code sent", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onVerificationCompleted(PhoneAuthCredential credential) {
                showLoading(false);
                // Auto-verification completed, handle user creation/login
                handlePhoneAuthSuccess(phoneNumber);
            }

            @Override
            public void onVerificationFailed(Exception e) {
                showLoading(false);
                Log.e(TAG, "Phone verification failed", e);

                // Provide specific error message based on the exception
                String errorMessage;

                if (e instanceof FirebaseTooManyRequestsException) {
                    errorMessage = "We have blocked all requests from this device due to unusual activity. Try again later.";
                    // Enforce a longer cooldown
                    preferences.edit().putLong(PREF_LAST_SMS_REQUEST_TIME, System.currentTimeMillis()).apply();
                    startCountdownTimer(SMS_COOLDOWN_MS);
                } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
                    errorMessage = "Invalid phone number format. Please check and try again.";
                } else if (e.getMessage() != null && e.getMessage().contains("quota")) {
                    errorMessage = "SMS quota exceeded. Please try again later.";
                    // Enforce a longer cooldown
                    preferences.edit().putLong(PREF_LAST_SMS_REQUEST_TIME, System.currentTimeMillis()).apply();
                    startCountdownTimer(SMS_COOLDOWN_MS);
                } else if (e.getMessage() != null && e.getMessage().contains("network")) {
                    errorMessage = "Network error. Check your connection and try again.";
                } else {
                    errorMessage = "Verification failed: " + e.getMessage();
                }

                Toast.makeText(PhoneAuthActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private boolean isValidPhoneNumber(String phoneNumber) {
        if (TextUtils.isEmpty(phoneNumber)) {
            return false;
        }

        // Remove any non-digit characters
        String cleaned = phoneNumber.replaceAll("[^\\d+]", "");

        // Indian numbers validation
        if (cleaned.startsWith("+91")) {
            return cleaned.length() == 13; // +91 plus 10 digits
        } else if (Pattern.matches("^[6-9]\\d{9}$", cleaned)) {
            // Indian mobile without country code (starts with 6-9 and is 10 digits)
            return true;
        } else {
            return false;
        }
    }

    private void resendVerificationCode() {
        if (TextUtils.isEmpty(phoneNumber)) {
            viewFlipper.setDisplayedChild(0);
            return;
        }

        // Record the current time for cooldown
        preferences.edit().putLong(PREF_LAST_SMS_REQUEST_TIME, System.currentTimeMillis()).apply();

        showLoading(true);

        authService.startPhoneVerification(phoneNumber, this, new AuthService.PhoneVerificationCallback() {
            @Override
            public void onCodeSent(String vId) {
                showLoading(false);
                verificationId = vId;

                // Clear the input and start SMS retriever again
                otpInputView.clearOTP();
                smsRetrieverHelper.startSMSRetriever();

                Toast.makeText(PhoneAuthActivity.this, "Verification code resent", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onVerificationCompleted(PhoneAuthCredential credential) {
                showLoading(false);
                handlePhoneAuthSuccess(phoneNumber);
            }

            @Override
            public void onVerificationFailed(Exception e) {
                showLoading(false);
                Log.e(TAG, "Phone verification failed on resend", e);

                String errorMessage;

                if (e instanceof FirebaseTooManyRequestsException) {
                    errorMessage = "We have blocked all requests from this device due to unusual activity. Try again later.";
                    startCountdownTimer(SMS_COOLDOWN_MS);
                } else if (e.getMessage() != null && e.getMessage().contains("quota")) {
                    errorMessage = "SMS quota exceeded. Please try again later.";
                    startCountdownTimer(SMS_COOLDOWN_MS);
                } else {
                    errorMessage = "Resend failed: " + e.getMessage();
                }

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

        showLoading(true);

        authService.verifyPhoneWithCode(verificationId, code, new AuthService.AuthCallback() {
            @Override
            public void onSuccess() {
                handlePhoneAuthSuccess(phoneNumber);
            }

            @Override
            public void onError(Exception e) {
                showLoading(false);
                Log.e(TAG, "Code verification failed", e);

                String errorMessage;
                if (e instanceof FirebaseAuthInvalidCredentialsException) {
                    errorMessage = "Invalid verification code. Please try again";
                    otpInputView.clearOTP();
                } else if (e.getMessage() != null && e.getMessage().contains("expired")) {
                    errorMessage = "Code expired. Please request a new code";
                } else {
                    errorMessage = "Code verification failed: " + e.getMessage();
                }

                Toast.makeText(PhoneAuthActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void handlePhoneAuthSuccess(final String phoneNumber) {
        // Check if user exists in database
        userRepository.getUserByPhoneNumber(phoneNumber, new UserRepository.OnUserFetchedListener() {
            @Override
            public void onSuccess(User user) {
                // User exists, save to session
                sessionManager.saveUserPhoneNumber(phoneNumber);
                showLoading(false);
                navigateToMain();
            }

            @Override
            public void onError(Exception e) {
                // Handle specific database errors
                if (e.getMessage() != null && e.getMessage().contains("User not found")) {
                    // User doesn't exist, create new user
                    createNewUser(phoneNumber);
                } else {
                    // Other database error
                    showLoading(false);
                    Log.e(TAG, "Database error during user check", e);
                    Toast.makeText(PhoneAuthActivity.this,
                            "Database error: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void createNewUser(String phoneNumber) {
        sessionManager.createNewUser(phoneNumber, new OnCompleteListener() {
            @Override
            public void onSuccess() {
                showLoading(false);
                navigateToMain();
            }

            @Override
            public void onError(Exception e) {
                showLoading(false);
                Log.e(TAG, "Failed to create user", e);

                String errorMessage = "Failed to create user: ";
                if (e.getMessage() != null && e.getMessage().contains("network")) {
                    errorMessage += "Network error. Please check your connection";
                } else if (e.getMessage() != null && e.getMessage().contains("permission")) {
                    errorMessage += "Permission denied. Please contact support";
                } else {
                    errorMessage += e.getMessage();
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

    private void showLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);

        // Disable UI interaction during loading
        sendCodeButton.setEnabled(!isLoading);
        verifyCodeButton.setEnabled(!isLoading && otpInputView.hasValidOTP());
        backButton.setEnabled(!isLoading);
        resendCodeText.setEnabled(!isLoading);

        if (otpInputView != null) {
            otpInputView.setEnabled(!isLoading);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up SMS retriever
        if (smsRetrieverHelper != null) {
            smsRetrieverHelper.unregisterReceiver();
        }

        // Clean up countdown timer
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}