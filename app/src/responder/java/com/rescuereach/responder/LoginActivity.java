package com.rescuereach.responder;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.rescuereach.R;
import com.rescuereach.data.model.Responder;
import com.rescuereach.service.auth.AuthService;
import com.rescuereach.service.auth.AuthServiceProvider;
import com.rescuereach.service.auth.ResponderSessionManager;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";

    private EditText emailEditText;
    private EditText passwordEditText;
    private Button loginButton;
    private TextView contactAdminText;
    private TextView versionText;
    private ProgressBar progressBar;

    private AuthService authService;
    private ResponderSessionManager sessionManager;

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
        contactAdminText = findViewById(R.id.contact_admin);
        versionText = findViewById(R.id.version_text);
        progressBar = findViewById(R.id.progress_bar);

        // Set app version
        versionText.setText("RescueReach Responder v1.0");

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
            // Show loading and attempt login
            showLoading(true);

            authService.signInWithEmailAndPassword(email, password, new AuthService.AuthCallback() {
                @Override
                public void onSuccess() {
                    handleLoginSuccess();
                }

                @Override
                public void onError(Exception e) {
                    showLoading(false);
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
                showLoading(false);
                navigateToMain();
            }

            @Override
            public void onError(Exception e) {
                showLoading(false);
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

    private void showLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!isLoading);
        emailEditText.setEnabled(!isLoading);
        passwordEditText.setEnabled(!isLoading);
    }
}