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
import com.rescuereach.data.repository.UserRepository;
import com.rescuereach.data.repository.RepositoryProvider;
import com.rescuereach.service.auth.AuthService;
import com.rescuereach.service.auth.AuthServiceProvider;
import com.rescuereach.service.auth.UserSessionManager;

public class PhoneAuthActivity extends AppCompatActivity {
    private static final String TAG = "PhoneAuthActivity";

    private EditText phoneEditText;
    private EditText codeEditText;
    private Button sendCodeButton;
    private Button verifyCodeButton;
    private ImageButton backButton;
    private TextView phoneDisplayText;
    private TextView resendCodeText;
    private ViewFlipper viewFlipper;
    private ProgressBar progressBar;

    private AuthService authService;
    private UserRepository userRepository;
    private UserSessionManager sessionManager;

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

        // Initialize views
        phoneEditText = findViewById(R.id.edit_phone);
        codeEditText = findViewById(R.id.edit_code);
        sendCodeButton = findViewById(R.id.button_send_code);
        verifyCodeButton = findViewById(R.id.button_verify_code);
        backButton = findViewById(R.id.button_back);
        phoneDisplayText = findViewById(R.id.text_phone_display);
        resendCodeText = findViewById(R.id.text_resend_code);
        viewFlipper = findViewById(R.id.viewFlipper);
        progressBar = findViewById(R.id.progress_bar);

        // Set up button click listeners
        sendCodeButton.setOnClickListener(v -> sendVerificationCode());
        verifyCodeButton.setOnClickListener(v -> verifyCode());
        backButton.setOnClickListener(v -> viewFlipper.setDisplayedChild(0));
        resendCodeText.setOnClickListener(v -> resendVerificationCode());
    }

    private void sendVerificationCode() {
        phoneNumber = phoneEditText.getText().toString().trim();

        if (TextUtils.isEmpty(phoneNumber)) {
            phoneEditText.setError("Phone number is required");
            return;
        }

        // Format phone number with country code if needed
        if (!phoneNumber.startsWith("+")) {
            phoneNumber = "+1" + phoneNumber; // Default to US country code
        }

        showLoading(true);

        authService.startPhoneVerification(phoneNumber, this, new AuthService.PhoneVerificationCallback() {
            @Override
            public void onCodeSent(String vId) {
                showLoading(false);
                verificationId = vId;
                phoneDisplayText.setText("Code sent to " + phoneNumber);
                viewFlipper.setDisplayedChild(1);
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
                Toast.makeText(PhoneAuthActivity.this,
                        "Verification failed: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
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
                Toast.makeText(PhoneAuthActivity.this,
                        "Resend failed: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void verifyCode() {
        String code = codeEditText.getText().toString().trim();

        if (TextUtils.isEmpty(code)) {
            codeEditText.setError("Verification code is required");
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
                Toast.makeText(PhoneAuthActivity.this,
                        "Code verification failed: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
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
                // User doesn't exist, create new user
                createNewUser(phoneNumber);
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
                Toast.makeText(PhoneAuthActivity.this,
                        "Failed to create user: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
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
    }
}