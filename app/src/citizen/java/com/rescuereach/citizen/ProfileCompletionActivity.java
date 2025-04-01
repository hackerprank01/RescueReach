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

import java.util.regex.Pattern;

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
        saveButton.setOnClickListener(v -> {
            // Validate and update profile
            if (validateInput()) {
                saveProfile();
            }
        });
    }

    private boolean validateInput() {
        boolean isValid = true;

        // Validate first name
        String firstName = firstNameEditText.getText().toString().trim();
        if (TextUtils.isEmpty(firstName)) {
            firstNameEditText.setError("First name is required");
            firstNameEditText.requestFocus();
            isValid = false;
        } else if (firstName.length() < 2) {
            firstNameEditText.setError("First name must be at least 2 characters");
            firstNameEditText.requestFocus();
            isValid = false;
        }

        // Validate last name
        String lastName = lastNameEditText.getText().toString().trim();
        if (TextUtils.isEmpty(lastName)) {
            lastNameEditText.setError("Last name is required");
            if (isValid) lastNameEditText.requestFocus();
            isValid = false;
        } else if (lastName.length() < 2) {
            lastNameEditText.setError("Last name must be at least 2 characters");
            if (isValid) lastNameEditText.requestFocus();
            isValid = false;
        }

        // Validate emergency contact
        String emergencyContact = emergencyContactEditText.getText().toString().trim();
        if (TextUtils.isEmpty(emergencyContact)) {
            emergencyContactEditText.setError("Emergency contact is required");
            if (isValid) emergencyContactEditText.requestFocus();
            isValid = false;
        } else if (!isValidPhoneNumber(emergencyContact)) {
            emergencyContactEditText.setError("Please enter a valid 10-digit phone number");
            if (isValid) emergencyContactEditText.requestFocus();
            isValid = false;
        }

        return isValid;
    }

    private boolean isValidPhoneNumber(String phoneNumber) {
        if (TextUtils.isEmpty(phoneNumber)) {
            return false;
        }

        // Remove any non-digit characters
        String cleaned = phoneNumber.replaceAll("[^\\d+]", "");

        // Valid formats: +91XXXXXXXXXX or just XXXXXXXXXX (10 digits)
        if (cleaned.startsWith("+91")) {
            return cleaned.length() == 10 && Pattern.matches("^[6-9]\\d{9}$", cleaned);
        } else {
            return cleaned.length() == 10 && Pattern.matches("^[6-9]\\d{9}$", cleaned);
        }
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