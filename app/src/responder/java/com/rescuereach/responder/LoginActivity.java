package com.rescuereach.responder;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.rescuereach.R;
import com.rescuereach.service.auth.AuthService;
import com.rescuereach.service.auth.AuthServiceProvider;
import com.rescuereach.service.auth.ResponderSessionManager;
import com.rescuereach.ui.common.LoadingDialog;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";

    private EditText emailEditText;
    private EditText passwordEditText;
    private Button loginButton;

    private AuthService authService;
    private ResponderSessionManager sessionManager;
    private LoadingDialog loadingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize services
        authService = AuthServiceProvider.getInstance().getAuthService();
        sessionManager = ResponderSessionManager.getInstance(this);

        // Initialize views
        emailEditText = findViewById(R.id.edit_email);
        passwordEditText = findViewById(R.id.edit_password);
        loginButton = findViewById(R.id.button_login);

        // Set up loading dialog
        loadingDialog = new LoadingDialog(this, "Logging in...");

        // Set up login button
        loginButton.setOnClickListener(v -> attemptLogin());
    }

    private void attemptLogin() {
        // Reset errors
        emailEditText.setError(null);
        passwordEditText.setError(null);

        // Get input values
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString();

        // Validate input
        boolean cancel = false;
        View focusView = null;

        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError("Password is required");
            focusView = passwordEditText;
            cancel = true;
        }

        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Email is required");
            focusView = emailEditText;
            cancel = true;
        }

        if (cancel) {
            // There was an error; focus the first form field with an error
            focusView.requestFocus();
        } else {
            // Show loading dialog and attempt login
            loadingDialog.show();

            authService.signInWithEmailAndPassword(email, password, new AuthService.AuthCallback() {
                @Override
                public void onSuccess() {
                    loadingDialog.dismiss();
                    handleLoginSuccess();
                }

                @Override
                public void onError(Exception e) {
                    loadingDialog.dismiss();
                    Log.e(TAG, "Login failed", e);
                    Toast.makeText(LoginActivity.this,
                            "Login failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void handleLoginSuccess() {
        // Load responder profile
        sessionManager.loadCurrentResponder(new ResponderSessionManager.OnResponderLoadedListener() {
            @Override
            public void onResponderLoaded(Responder responder) {
                navigateToMain();
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Failed to load responder profile", e);
                Toast.makeText(LoginActivity.this,
                        "Failed to load responder profile. Please contact administrator.",
                        Toast.LENGTH_LONG).show();

                // Sign out since we couldn't load the profile
                authService.signOut(new AuthService.AuthCallback() {
                    @Override
                    public void onSuccess() {
                        // Do nothing, already in login screen
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Failed to sign out", e);
                    }
                });
            }
        });
    }

    private void navigateToMain() {
        startActivity(new Intent(this, ResponderMainActivity.class));
        finish();
    }
}