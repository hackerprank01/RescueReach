//package com.rescuereach.responder;
//
//import android.content.Intent;
//import android.os.Bundle;
//import android.text.TextUtils;
//import android.util.Log;
//import android.util.Patterns;
//import android.view.View;
//import android.widget.Button;
//import android.widget.CheckBox;
//import android.widget.EditText;
//import android.widget.FrameLayout;
//import android.widget.ProgressBar;
//import android.widget.TextView;
//import android.widget.Toast;
//
//import androidx.appcompat.app.AlertDialog;
//import androidx.appcompat.app.AppCompatActivity;
//
//import com.google.android.material.textfield.TextInputLayout;
//import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
//import com.google.firebase.auth.FirebaseAuthInvalidUserException;
//import com.rescuereach.R;
////import com.rescuereach.data.model.Responder;
//import com.rescuereach.service.auth.AuthService;
//import com.rescuereach.service.auth.AuthServiceProvider;
//import com.rescuereach.service.auth.ResponderSessionManager;
//
//import java.util.Date;
//
//public class LoginActivity extends AppCompatActivity {
//    private static final String TAG = "LoginActivity";
//    private static final int MAX_LOGIN_ATTEMPTS = 5;
//    private static final long LOCKOUT_DURATION_MS = 30 * 60 * 1000; // 30 minutes
//
//    private TextInputLayout emailLayout;
//    private TextInputLayout passwordLayout;
//    private EditText emailEditText;
//    private EditText passwordEditText;
//    private CheckBox rememberMeCheckbox;
//    private Button loginButton;
//    private TextView contactAdminText;
//
//    private FrameLayout loadingOverlay;
//    private ProgressBar progressBar;
//
//    private AuthService authService;
//    private ResponderSessionManager sessionManager;
//
//    private int loginAttempts = 0;
//    private long lockoutEndTime = 0;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_login);
//
//        // Initialize services
//        authService = AuthServiceProvider.getInstance().getAuthService();
//        sessionManager = ResponderSessionManager.getInstance(this);
//
//        // Initialize views
//        initializeViews();
//
//        // Check if user is already logged in
//        if (authService.isLoggedIn() && sessionManager.isRememberMeEnabled()) {
//            navigateToMain();
//            return;
//        }
//
//        // Restore saved email if remember me was checked
//        if (sessionManager.isRememberMeEnabled()) {
//            String savedEmail = sessionManager.getSavedEmail();
//            if (savedEmail != null && !savedEmail.isEmpty()) {
//                emailEditText.setText(savedEmail);
//                rememberMeCheckbox.setChecked(true);
//            }
//        }
//
//        // Set up listeners
//        setupListeners();
//    }
//
//    private void initializeViews() {
//        emailLayout = findViewById(R.id.email_layout);
//        passwordLayout = findViewById(R.id.password_layout);
//        emailEditText = findViewById(R.id.edit_email);
//        passwordEditText = findViewById(R.id.edit_password);
//        rememberMeCheckbox = findViewById(R.id.checkbox_remember_me);
//        loginButton = findViewById(R.id.button_login);
//        contactAdminText = findViewById(R.id.contact_admin);
//        loadingOverlay = findViewById(R.id.loading_overlay);
//        progressBar = findViewById(R.id.progress_bar);
//    }
//
//    private void setupListeners() {
//        // Set up login button
//        loginButton.setOnClickListener(v -> attemptLogin());
//
//        // Text change listeners to clear errors as user types
//        emailEditText.addTextChangedListener(new SimpleTextWatcher() {
//            @Override
//            public void onTextChanged(CharSequence s, int start, int before, int count) {
//                emailLayout.setError(null);
//            }
//        });
//
//        passwordEditText.addTextChangedListener(new SimpleTextWatcher() {
//            @Override
//            public void onTextChanged(CharSequence s, int start, int before, int count) {
//                passwordLayout.setError(null);
//            }
//        });
//    }
//
//    private void attemptLogin() {
//        // Check if account is locked out
//        if (System.currentTimeMillis() < lockoutEndTime) {
//            long remainingSeconds = (lockoutEndTime - System.currentTimeMillis()) / 1000;
//            long minutes = remainingSeconds / 60;
//            long seconds = remainingSeconds % 60;
//
//            showErrorDialog("Account Temporarily Locked",
//                    "Too many failed login attempts. Please try again in " +
//                            minutes + " minute" + (minutes != 1 ? "s" : "") + " and " +
//                            seconds + " second" + (seconds != 1 ? "s" : "") + ".");
//            return;
//        }
//
//        // Reset errors
//        emailLayout.setError(null);
//        passwordLayout.setError(null);
//
//        // Get input values
//        String email = emailEditText.getText().toString().trim();
//        String password = passwordEditText.getText().toString();
//
//        // Validate input
//        boolean isValid = true;
//
//        if (TextUtils.isEmpty(password)) {
//            passwordLayout.setError("Password is required");
//            isValid = false;
//        } else if (password.length() < 6) {
//            passwordLayout.setError("Password must be at least 6 characters");
//            isValid = false;
//        }
//
//        if (TextUtils.isEmpty(email)) {
//            emailLayout.setError("Email is required");
//            isValid = false;
//        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
//            emailLayout.setError("Please enter a valid email address");
//            isValid = false;
//        }
//
//        if (!isValid) {
//            return;
//        }
//
//        // Show loading and attempt login
//        showLoading(true);
//
//        authService.signInWithEmailAndPassword(email, password, new AuthService.AuthCallback() {
//            @Override
//            public void onSuccess() {
//                // Reset login attempts on success
//                loginAttempts = 0;
//
//                // Save email if remember me is checked
//                if (rememberMeCheckbox.isChecked()) {
//                    sessionManager.saveRememberMe(true);
//                    sessionManager.saveEmail(email);
//                } else {
//                    sessionManager.saveRememberMe(false);
//                    sessionManager.clearSavedEmail();
//                }
//
//                handleLoginSuccess();
//            }
//
//            @Override
//            public void onError(Exception e) {
//                showLoading(false);
//                loginAttempts++;
//
//                // Handle lockout if too many attempts
//                if (loginAttempts >= MAX_LOGIN_ATTEMPTS) {
//                    lockoutEndTime = System.currentTimeMillis() + LOCKOUT_DURATION_MS;
//                    showErrorDialog("Account Temporarily Locked",
//                            "Too many failed login attempts. Please try again in 30 minutes or contact your administrator at admin_rescuereach@gmail.com");
//                    return;
//                }
//
//                Log.e(TAG, "Login failed", e);
//
//                // Handle specific error cases
//                if (e instanceof FirebaseAuthInvalidUserException) {
//                    emailLayout.setError("No account exists with this email. Contact your administrator if this is incorrect.");
//                } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
//                    passwordLayout.setError("Incorrect password. Contact your administrator if you forgot your password.");
//                } else {
//                    Toast.makeText(LoginActivity.this,
//                            "Login failed: " + e.getMessage(),
//                            Toast.LENGTH_LONG).show();
//                }
//            }
//        });
//    }
//
//    private void handleLoginSuccess() {
//        // Load responder profile
//        sessionManager.loadCurrentResponder(new ResponderSessionManager.OnResponderLoadedListener() {
//            @Override
//            public void onResponderLoaded(Responder responder) {
//                // Update last login time
//                responder.setLastLoginAt(new Date());
//
//                // Update responder in database
//                updateResponderLastLogin(responder);
//
//                showLoading(false);
//                navigateToMain();
//            }
//
//            @Override
//            public void onError(Exception e) {
//                showLoading(false);
//                Log.e(TAG, "Failed to load responder profile", e);
//
//                showErrorDialog("Profile Error",
//                        "Failed to load responder profile. Please contact your administrator at admin_rescuereach@gmail.com");
//
//                // Sign out since we couldn't load the profile
//                authService.signOut(new AuthService.AuthCallback() {
//                    @Override
//                    public void onSuccess() {
//                        // Do nothing, already showing error
//                    }
//
//                    @Override
//                    public void onError(Exception e) {
//                        Log.e(TAG, "Failed to sign out", e);
//                    }
//                });
//            }
//        });
//    }
//
//    private void updateResponderLastLogin(Responder responder) {
//        // This updates the last login time in the database
//        sessionManager.updateResponderLastLogin(responder, new AuthService.AuthCallback() {
//            @Override
//            public void onSuccess() {
//                Log.d(TAG, "Last login time updated successfully");
//            }
//
//            @Override
//            public void onError(Exception e) {
//                Log.e(TAG, "Failed to update last login time", e);
//            }
//        });
//    }
//
//    private void showErrorDialog(String title, String message) {
//        if (isFinishing()) return;
//
//        new AlertDialog.Builder(this)
//                .setTitle(title)
//                .setMessage(message)
//                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
//                .show();
//    }
//
//    private void navigateToMain() {
//        startActivity(new Intent(this, ResponderMainActivity.class));
//        finish();
//    }
//
//    private void showLoading(boolean isLoading) {
//        loadingOverlay.setVisibility(isLoading ? View.VISIBLE : View.GONE);
//
//        if (isLoading && loadingOverlay.getVisibility() == View.VISIBLE) {
//            // Animate fade in
//            loadingOverlay.setAlpha(0f);
//            loadingOverlay.animate()
//                    .alpha(1f)
//                    .setDuration(200)
//                    .start();
//        }
//    }
//
//    // Simple TextWatcher that only requires overriding necessary methods
//    private abstract static class SimpleTextWatcher implements android.text.TextWatcher {
//        @Override
//        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
//            // Not used
//        }
//
//        @Override
//        public void afterTextChanged(android.text.Editable s) {
//            // Not used
//        }
//    }
//}