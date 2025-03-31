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

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
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
    private static final long SMS_COOLDOWN_MS = 30000; // 60 seconds cooldown
    private static final String PREF_NAME = "phone_auth_prefs";
    private static final String PREF_LAST_SMS_REQUEST_TIME = "last_sms_request_time";

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
    private CountDownTimer countDownTimer;
    private SharedPreferences preferences;

    private AuthService authService;
    private UserRepository userRepository;
    private UserSessionManager sessionManager;
    private SMSRetrieverHelper smsRetrieverHelper;

    private String verificationId;
    private String phoneNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_auth);

        // Check Google Play Services availability
        checkGooglePlayServices();
        
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

        // Add countdown text after send button
        countdownText = new TextView(this);
        countdownText.setId(View.generateViewId());
        countdownText.setTextColor(getResources().getColor(android.R.color.holo_red_light));
        countdownText.setVisibility(View.GONE);

        // Find the parent layout containing the send button
        ViewGroup sendButtonParent = (ViewGroup) sendCodeButton.getParent();
        int index = sendButtonParent.indexOfChild(sendCodeButton);
        sendButtonParent.addView(countdownText, index + 1);

        // Set the layout parameters for the countdown text
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) countdownText.getLayoutParams();
        params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        params.topMargin = 16;
        countdownText.setLayoutParams(params);

        // Set up listeners
        otpInputView.setOTPCompletionListener(this);

        // Set up SMS retriever
        setupSMSRetriever();

        // Set up button click listeners
        sendCodeButton.setOnClickListener(v -> {
            if (canRequestSmsVerification()) {
                sendVerificationCode();
            } else {
                long remainingSeconds = getRemainingCooldownTimeSeconds();
                Toast.makeText(this,
                        "Please wait " + remainingSeconds + " seconds before requesting another code",
                        Toast.LENGTH_SHORT).show();
            }
        });

        verifyCodeButton.setOnClickListener(v -> verifyCode());
        backButton.setOnClickListener(v -> viewFlipper.setDisplayedChild(0));
        resendCodeText.setOnClickListener(v -> resendVerificationCode());;

        // Check if we're in a cooldown period
        checkAndShowCooldown();
    }

    private void checkGooglePlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);

        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                apiAvailability.getErrorDialog(this, resultCode, 9000,
                                dialog -> Toast.makeText(this, "This app requires Google Play Services to function properly",
                                        Toast.LENGTH_LONG).show())
                        .show();
            } else {
                Toast.makeText(this, "This device does not support Google Play Services which is required",
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }
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
                // Just log the error but don't show to user to avoid confusion
                // It will fallback to manual input
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
                    // Force a longer cooldown
                    preferences.edit().putLong(PREF_LAST_SMS_REQUEST_TIME, System.currentTimeMillis()).apply();
                    startCountdownTimer(SMS_COOLDOWN_MS);
                } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
                    errorMessage = "Invalid phone number format. Please check and try again.";
                } else if (e instanceof FirebaseException && e.getMessage() != null) {
                    if (e.getMessage().contains("quota")) {
                        errorMessage = "SMS quota exceeded. Please try again later.";
                        // Force a longer cooldown
                        preferences.edit().putLong(PREF_LAST_SMS_REQUEST_TIME, System.currentTimeMillis()).apply();
                        startCountdownTimer(SMS_COOLDOWN_MS);
                    } else if (e.getMessage().contains("invalid format")) {
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

        // No cooldown for resend - just show a loading indicator
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

                if (e instanceof FirebaseTooManyRequestsException ||
                        (e.getMessage() != null && e.getMessage().contains("quota"))) {
                    errorMessage = "We have blocked all requests from this device due to unusual activity. Try again later.";
                    // Force a longer cooldown
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

        // Only enable verify button if OTP is complete
        if (otpInputView != null) {
            String currentOtp = otpInputView.getOTP();
            verifyCodeButton.setEnabled(!isLoading && currentOtp.length() == 6);
        } else {
            verifyCodeButton.setEnabled(!isLoading);
        }

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