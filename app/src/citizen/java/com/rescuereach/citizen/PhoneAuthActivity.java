package com.rescuereach.citizen;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

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
import com.rescuereach.ui.common.LoadingDialog;

public class PhoneAuthActivity extends AppCompatActivity {
    private static final String TAG = "PhoneAuthActivity";

    private EditText phoneEditText;
    private EditText codeEditText;
    private Button sendCodeButton;
    private Button verifyCodeButton;
    private View phoneLayout;
    private View codeLayout;

    private AuthService authService;
    private UserRepository userRepository;
    private UserSessionManager sessionManager;
    private LoadingDialog loadingDialog;

    private String verificationId;

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
        phoneLayout = findViewById(R.id.layout_phone);
        codeLayout = findViewById(R.id.layout_code);

        // Set up loading dialog
        loadingDialog = new LoadingDialog(this, "Please wait...");

        // Set up button click listeners
        sendCodeButton.setOnClickListener(v -> sendVerificationCode());
        verifyCodeButton.setOnClickListener(v -> verifyCode());

        // Initially show phone input layout
        showPhoneLayout();
    }

    private void showPhoneLayout() {
        phoneLayout.setVisibility(View.VISIBLE);
        codeLayout.setVisibility(View.GONE);
    }

    private void showCodeLayout() {
        phoneLayout.setVisibility(View.GONE);
        codeLayout.setVisibility(View.VISIBLE);
    }

    private void sendVerificationCode() {
        String phoneNumber = phoneEditText.getText().toString().trim();

        if (TextUtils.isEmpty(phoneNumber)) {
            phoneEditText.setError("Phone number is required");
            return;
        }

        // Format phone number with country code if needed
        if (!phoneNumber.startsWith("+")) {
            phoneNumber = "+1" + phoneNumber; // Default to US country code
        }

        final String formattedPhoneNumber = phoneNumber;

        loadingDialog.updateMessage("Sending verification code...");
        loadingDialog.show();

        authService.startPhoneVerification(formattedPhoneNumber, this, new AuthService.PhoneVerificationCallback() {
            @Override
            public void onCodeSent(String vId) {
                loadingDialog.dismiss();
                verificationId = vId;
                showCodeLayout();
                Toast.makeText(PhoneAuthActivity.this, "Verification code sent", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onVerificationCompleted(PhoneAuthCredential credential) {
                loadingDialog.dismiss();
                // Auto-verification completed, handle user creation/login
                handlePhoneAuthSuccess(formattedPhoneNumber);
            }

            @Override
            public void onVerificationFailed(Exception e) {
                loadingDialog.dismiss();
                Log.e(TAG, "Phone verification failed", e);
                Toast.makeText(PhoneAuthActivity.this,
                        "Verification failed: " + e.getMessage(),
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

        loadingDialog.updateMessage("Verifying code...");
        loadingDialog.show();

        authService.verifyPhoneWithCode(verificationId, code, new AuthService.AuthCallback() {
            @Override
            public void onSuccess() {
                handlePhoneAuthSuccess(phoneEditText.getText().toString().trim());
            }

            @Override
            public void onError(Exception e) {
                loadingDialog.dismiss();
                Log.e(TAG, "Code verification failed", e);
                Toast.makeText(PhoneAuthActivity.this,
                        "Code verification failed: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void handlePhoneAuthSuccess(final String phoneNumber) {
        loadingDialog.updateMessage("Finalizing login...");

        // Check if user exists in database
        userRepository.getUserByPhoneNumber(phoneNumber, new UserRepository.OnUserFetchedListener() {
            @Override
            public void onSuccess(User user) {
                // User exists, save to session
                sessionManager.saveUserPhoneNumber(phoneNumber);
                loadingDialog.dismiss();
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
                loadingDialog.dismiss();
                navigateToMain();
            }

            @Override
            public void onError(Exception e) {
                loadingDialog.dismiss();
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
}