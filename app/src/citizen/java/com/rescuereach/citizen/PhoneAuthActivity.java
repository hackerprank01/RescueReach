package com.rescuereach.citizen;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.appcompat.app.AppCompatActivity;

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

public class PhoneAuthActivity extends AppCompatActivity implements OTPInputView.OTPCompletionListener {
    private static final String TAG = "PhoneAuthActivity";

    private EditText phoneEditText;
    private Button sendCodeButton;
    private Button verifyCodeButton;
    private ImageButton backButton;
    private TextView phoneDisplayText;
    private TextView resendCodeText;
    private ViewFlipper viewFlipper;
    private ProgressBar progressBar;
    private OTPInputView otpInputView;

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

        // Set up listeners
        otpInputView.setOTPCompletionListener(this);

        // Set up SMS retriever
        setupSMSRetriever();

        // Set up button click listeners
        sendCodeButton.setOnClickListener(v -> sendVerificationCode());
        verifyCodeButton.setOnClickListener(v -> verifyCode());
        backButton.setOnClickListener(v -> viewFlipper.setDisplayedChild(0));
        resendCodeText.setOnClickListener(v -> resendVerificationCode());
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
                        "SMS auto-detection failed: " + e.getMessage(),
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

        if (TextUtils.isEmpty(phoneNumber)) {
            phoneEditText.setError("Phone number is required");
            return;
        }

        // Format phone number with country code if needed
        if (!phoneNumber.startsWith("+")) {
            phoneNumber = "+91" + phoneNumber; // Default to US country code
        }

        showLoading(true);

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
                String errorMessage = "Verification failed: ";

                if (e.getMessage().contains("invalid format")) {
                    errorMessage += "The phone number format is invalid";
                } else if (e.getMessage().contains("quota")) {
                    errorMessage += "Too many requests. Try again later";
                } else if (e.getMessage().contains("network")) {
                    errorMessage += "Network error. Check your connection";
                } else {
                    errorMessage += e.getMessage();
                }

                Toast.makeText(PhoneAuthActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void resendVerificationCode() {
        if (TextUtils.isEmpty(phoneNumber)) {
            viewFlipper.setDisplayedChild(0);
            return;
        }

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

                String errorMessage = "Resend failed: ";
                if (e.getMessage().contains("quota")) {
                    errorMessage += "Too many attempts. Try again later";
                } else {
                    errorMessage += e.getMessage();
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

                String errorMessage = "Code verification failed: ";
                if (e.getMessage().contains("invalid code")) {
                    errorMessage = "Invalid verification code. Please try again";
                    otpInputView.clearOTP();
                } else if (e.getMessage().contains("expired")) {
                    errorMessage = "Code expired. Please request a new code";
                } else {
                    errorMessage += e.getMessage();
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
                if (e.getMessage().contains("User not found")) {
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
                if (e.getMessage().contains("network")) {
                    errorMessage += "Network error. Please check your connection";
                } else if (e.getMessage().contains("permission")) {
                    errorMessage += "Permission denied. Please contact support";
                } else {
                    errorMessage += e.getMessage();
                }

                Toast.makeText(PhoneAuthActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void navigateToMain() {
        startActivity(new Intent(this, CitizenMainActivity.class));
        finish();
    }

    private void showLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);

        // Disable UI interaction during loading
        sendCodeButton.setEnabled(!isLoading);
        verifyCodeButton.setEnabled(!isLoading);
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
    }
}