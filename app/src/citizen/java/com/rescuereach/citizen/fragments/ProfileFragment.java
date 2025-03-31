package com.rescuereach.citizen.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.rescuereach.R;
import com.rescuereach.data.model.User;
import com.rescuereach.data.repository.OnCompleteListener;
import com.rescuereach.service.auth.UserSessionManager;

public class ProfileFragment extends Fragment {
    private static final String TAG = "ProfileFragment";

    // UI Components
    private TextView fullNameTextView;
    private TextView phoneTextView;
    private TextView phoneEditTextView; // Non-editable phone in edit mode
    private EditText firstNameEditText;
    private EditText lastNameEditText;
    private EditText emergencyContactEditText;
    private Button updateProfileButton;
    private Button editProfileButton;
    private ProgressBar progressBar;
    private MaterialCardView viewProfileCardView;
    private MaterialCardView editProfileCardView;

    // Services
    private UserSessionManager sessionManager;

    // State variables
    private boolean isEditing = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize services
        sessionManager = UserSessionManager.getInstance(requireContext());

        // Initialize UI elements
        initializeViews(view);

        // Set up event listeners
        setupListeners();

        // Load user data
        loadUserData();
    }

    private void initializeViews(View view) {
        // Find view profile elements
        viewProfileCardView = view.findViewById(R.id.card_view_profile);
        fullNameTextView = view.findViewById(R.id.text_full_name);
        phoneTextView = view.findViewById(R.id.text_phone);
        editProfileButton = view.findViewById(R.id.button_edit_profile);

        // Find edit profile elements
        editProfileCardView = view.findViewById(R.id.card_edit_profile);
        phoneEditTextView = view.findViewById(R.id.text_phone_edit);
        firstNameEditText = view.findViewById(R.id.edit_first_name);
        lastNameEditText = view.findViewById(R.id.edit_last_name);
        emergencyContactEditText = view.findViewById(R.id.edit_emergency_contact);
        updateProfileButton = view.findViewById(R.id.button_update_profile);

        // Progress indicator
        progressBar = view.findViewById(R.id.progress_bar);

        // Initially show view mode
        setEditMode(false);
    }

    private void setupListeners() {
        // Set click listener for edit button
        editProfileButton.setOnClickListener(v -> {
            // Switch to edit mode
            prepareForEditing();
            setEditMode(true);
        });

        // Set click listener for update button
        updateProfileButton.setOnClickListener(v -> {
            // Validate and update profile
            if (validateInput()) {
                updateProfile();
            }
        });

        // Add text change listeners for first name and last name to update full name in real-time
        TextWatcher nameChangeWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                // Clear any error messages when user types
                clearErrors();
            }
        };

        firstNameEditText.addTextChangedListener(nameChangeWatcher);
        lastNameEditText.addTextChangedListener(nameChangeWatcher);
        emergencyContactEditText.addTextChangedListener(nameChangeWatcher);
    }

    private void loadUserData() {
        showLoading(true);

        sessionManager.loadCurrentUser(new UserSessionManager.OnUserLoadedListener() {
            @Override
            public void onUserLoaded(User user) {
                if (isAdded() && getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        showLoading(false);
                        displayUserData(user);
                    });
                }
            }

            @Override
            public void onError(Exception e) {
                if (isAdded() && getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        showLoading(false);
                        // Display cached data from preferences if available
                        displayCachedUserData();

                        // Show error toast
                        Toast.makeText(requireContext(),
                                "Error loading profile: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void displayUserData(User user) {
        if (user != null) {
            // Display user data in view mode
            fullNameTextView.setText(user.getFullName());
            phoneTextView.setText(user.getPhoneNumber());
            phoneEditTextView.setText(user.getPhoneNumber());

            // Also populate edit fields (hidden initially)
            firstNameEditText.setText(user.getFirstName());
            lastNameEditText.setText(user.getLastName());
            emergencyContactEditText.setText(user.getEmergencyContact());
        }
    }

    private void displayCachedUserData() {
        // Use data from SharedPreferences
        String phonNumber = sessionManager.getSavedPhoneNumber();
        fullNameTextView.setText(sessionManager.getFullName());
        phoneTextView.setText(phonNumber);
        phoneEditTextView.setText(phonNumber);

        firstNameEditText.setText(sessionManager.getFirstName());
        lastNameEditText.setText(sessionManager.getLastName());
        emergencyContactEditText.setText(sessionManager.getEmergencyContact());
    }

    private void prepareForEditing() {
        // Make sure edit form has latest data
        firstNameEditText.setText(sessionManager.getFirstName());
        lastNameEditText.setText(sessionManager.getLastName());
        emergencyContactEditText.setText(sessionManager.getEmergencyContact());
        phoneEditTextView.setText(sessionManager.getSavedPhoneNumber());

        // Clear any previous error states
        clearErrors();
    }

    private void clearErrors() {
        firstNameEditText.setError(null);
        lastNameEditText.setError(null);
        emergencyContactEditText.setError(null);
    }

    private boolean validateInput() {
        boolean isValid = true;

        // Validate first name
        String firstName = firstNameEditText.getText().toString().trim();
        if (TextUtils.isEmpty(firstName)) {
            firstNameEditText.setError("First name is required");
            firstNameEditText.requestFocus();
            isValid = false;
        }

        // Validate last name
        String lastName = lastNameEditText.getText().toString().trim();
        if (TextUtils.isEmpty(lastName)) {
            lastNameEditText.setError("Last name is required");
            lastNameEditText.requestFocus();
            isValid = false;
        }

        // Validate emergency contact
        String emergencyContact = emergencyContactEditText.getText().toString().trim();
        if (TextUtils.isEmpty(emergencyContact)) {
            emergencyContactEditText.setError("Emergency contact is required");
            emergencyContactEditText.requestFocus();
            isValid = false;
        } else if (!isValidPhoneNumber(emergencyContact)) {
            emergencyContactEditText.setError("Please enter a valid phone number");
            emergencyContactEditText.requestFocus();
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
            return cleaned.length() == 13;
        } else {
            return cleaned.length() == 10;
        }
    }

    private void updateProfile() {
        String firstName = firstNameEditText.getText().toString().trim();
        String lastName = lastNameEditText.getText().toString().trim();
        String emergencyContact = emergencyContactEditText.getText().toString().trim();

        showLoading(true);

        sessionManager.updateUserProfile(firstName, lastName, emergencyContact, new OnCompleteListener() {
            @Override
            public void onSuccess() {
                if (isAdded() && getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        showLoading(false);

                        // Update display with new data
                        fullNameTextView.setText(firstName + " " + lastName);

                        // Switch back to view mode
                        setEditMode(false);

                        Toast.makeText(requireContext(),
                                "Profile updated successfully",
                                Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onError(Exception e) {
                if (isAdded() && getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        showLoading(false);

                        Toast.makeText(requireContext(),
                                "Failed to update profile: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    });
                }
            }
        });
    }

    private void setEditMode(boolean isEdit) {
        isEditing = isEdit;

        // Toggle visibility between view and edit modes
        viewProfileCardView.setVisibility(isEdit ? View.GONE : View.VISIBLE);
        editProfileCardView.setVisibility(isEdit ? View.VISIBLE : View.GONE);
    }

    private void showLoading(boolean isLoading) {
        if (progressBar != null) {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }

        // Disable UI interaction during loading
        if (editProfileButton != null) {
            editProfileButton.setEnabled(!isLoading);
        }

        if (updateProfileButton != null) {
            updateProfileButton.setEnabled(!isLoading);
        }

        if (firstNameEditText != null) {
            firstNameEditText.setEnabled(!isLoading);
        }

        if (lastNameEditText != null) {
            lastNameEditText.setEnabled(!isLoading);
        }

        if (emergencyContactEditText != null) {
            emergencyContactEditText.setEnabled(!isLoading);
        }
    }
}