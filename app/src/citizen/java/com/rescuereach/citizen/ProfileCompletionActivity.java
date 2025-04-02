package com.rescuereach.citizen;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.rescuereach.R;
import com.rescuereach.data.repository.OnCompleteListener;
import com.rescuereach.service.auth.UserSessionManager;

public class ProfileCompletionActivity extends AppCompatActivity {
    private static final String TAG = "ProfileCompletionActivity";

    private EditText firstNameEditText;
    private EditText lastNameEditText;
    private EditText emergencyContactEditText;
    private Button saveButton;
    private ProgressBar progressBar;

    private UserSessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_completion);

        // Initialize services
        sessionManager = UserSessionManager.getInstance(this);

        // Initialize views
        firstNameEditText = findViewById(R.id.edit_first_name);
        lastNameEditText = findViewById(R.id.edit_last_name);
        emergencyContactEditText = findViewById(R.id.edit_emergency_contact);
        saveButton = findViewById(R.id.button_save);
        progressBar = findViewById(R.id.progress_bar);

        // Set up button click listener
        saveButton.setOnClickListener(v -> saveProfile());
    }

    private void saveProfile() {
        // Validate input
        String firstName = firstNameEditText.getText().toString().trim();
        String lastName = lastNameEditText.getText().toString().trim();
        String emergencyContact = emergencyContactEditText.getText().toString().trim();

        if (TextUtils.isEmpty(firstName)) {
            firstNameEditText.setError("First name is required");
            firstNameEditText.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(lastName)) {
            lastNameEditText.setError("Last name is required");
            lastNameEditText.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(emergencyContact)) {
            emergencyContactEditText.setError("Emergency contact is required");
            emergencyContactEditText.requestFocus();
            return;
        }

        showLoading(true);

        // Save profile
        sessionManager.updateUserProfile(firstName, lastName, emergencyContact, new OnCompleteListener() {
            @Override
            public void onSuccess() {
                showLoading(false);
                Toast.makeText(ProfileCompletionActivity.this,
                        "Profile saved successfully",
                        Toast.LENGTH_SHORT).show();
                navigateToMain();
            }

            @Override
            public void onError(Exception e) {
                showLoading(false);
                Log.e(TAG, "Failed to save profile", e);
                Toast.makeText(ProfileCompletionActivity.this,
                        "Failed to save profile: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        saveButton.setEnabled(!isLoading);
        firstNameEditText.setEnabled(!isLoading);
        lastNameEditText.setEnabled(!isLoading);
        emergencyContactEditText.setEnabled(!isLoading);
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, CitizenMainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}