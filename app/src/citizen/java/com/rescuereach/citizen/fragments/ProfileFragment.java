package com.rescuereach.citizen.fragments;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.rescuereach.R;
import com.rescuereach.data.model.User;
import com.rescuereach.data.repository.OnCompleteListener;
import com.rescuereach.service.auth.UserSessionManager;

import java.util.regex.Pattern;

public class ProfileFragment extends Fragment {
    private static final String TAG = "ProfileFragment";

    // UI Components
    private TextView fullNameTextView;
    private TextView phoneTextView;
    private TextView emergencyContactTextView;
    private TextView phoneEditTextView; // Non-editable phone in edit mode
    private EditText firstNameEditText;
    private EditText lastNameEditText;
    private EditText emergencyContactEditText;
    private Button updateProfileButton;
    private Button cancelUpdateButton;
    private Button editProfileButton;
    private ProgressBar progressBar;
    private MaterialCardView viewProfileCardView;
    private MaterialCardView editProfileCardView;

    // Services
    private UserSessionManager sessionManager;

    // State variables
    private boolean isEditing = false;
    private boolean hasUnsavedChanges = false;

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

        // Display cached data immediately to improve perceived performance
        displayCachedUserData();

        // Then load latest data from server
        loadUserData();
    }

    private void initializeViews(View view) {
        // Find view profile elements
        viewProfileCardView = view.findViewById(R.id.card_view_profile);
        fullNameTextView = view.findViewById(R.id.text_full_name);
        phoneTextView = view.findViewById(R.id.text_phone);
        emergencyContactTextView = view.findViewById(R.id.text_emergency_contact);
        editProfileButton = view.findViewById(R.id.button_edit_profile);

        // Find edit profile elements
        editProfileCardView = view.findViewById(R.id.card_edit_profile);
        phoneEditTextView = view.findViewById(R.id.text_phone_edit);
        firstNameEditText = view.findViewById(R.id.edit_first_name);
        lastNameEditText = view.findViewById(R.id.edit_last_name);
        emergencyContactEditText = view.findViewById(R.id.edit_emergency_contact);
        updateProfileButton = view.findViewById(R.id.button_update_profile);
        cancelUpdateButton = view.findViewById(R.id.button_cancel_update);

        // Loading element
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

        // Set click listener for cancel button
        cancelUpdateButton.setOnClickListener(v -> {
            // Check if there are unsaved changes
            if (hasUnsavedChanges) {
                showDiscardChangesDialog();
            } else {
                // No changes, just switch back to view mode
                setEditMode(false);
            }
        });

        // Add text change watchers for validation and change tracking
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Mark that we have unsaved changes
                hasUnsavedChanges = true;
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Clear any error messages when user types
                clearErrors();
            }
        };

        firstNameEditText.addTextChangedListener(textWatcher);
        lastNameEditText.addTextChangedListener(textWatcher);
        emergencyContactEditText.addTextChangedListener(textWatcher);
    }

    private void loadUserData() {
        // Don't show the full loading overlay since we're already showing cached data
        // Just show the progress bar
        progressBar.setVisibility(View.VISIBLE);

        sessionManager.loadCurrentUser(new UserSessionManager.OnUserLoadedListener() {
            @Override
            public void onUserLoaded(User user) {
                if (isAdded() && getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        displayUserData(user);
                    });
                }
            }

            @Override
            public void onError(Exception e) {
                if (isAdded() && getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);

                        // We already displayed cached data, so just show a toast about the fetch error
                        Toast.makeText(requireContext(),
                                "Error refreshing profile data",
                                Toast.LENGTH_SHORT).show();

                        Log.e(TAG, "Error loading profile", e);
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
            // Make sure we have an emergency contact view in layout
            if (emergencyContactTextView != null) {
                emergencyContactTextView.setText(user.getEmergencyContact());
            }
            phoneEditTextView.setText(user.getPhoneNumber());

            // Also populate edit fields (hidden initially)
            firstNameEditText.setText(user.getFirstName());
            lastNameEditText.setText(user.getLastName());
            emergencyContactEditText.setText(user.getEmergencyContact());

            // Reset unsaved changes flag
            hasUnsavedChanges = false;
        }
    }

    private void displayCachedUserData() {
        // Use data from SharedPreferences for immediate display
        String phoneNumber = sessionManager.getSavedPhoneNumber();
        fullNameTextView.setText(sessionManager.getFullName());
        phoneTextView.setText(phoneNumber);
        // Make sure we have an emergency contact view in layout
        if (emergencyContactTextView != null) {
            emergencyContactTextView.setText(sessionManager.getEmergencyContact());
        }
        phoneEditTextView.setText(phoneNumber);

        firstNameEditText.setText(sessionManager.getFirstName());
        lastNameEditText.setText(sessionManager.getLastName());
        emergencyContactEditText.setText(sessionManager.getEmergencyContact());

        // Reset unsaved changes flag
        hasUnsavedChanges = false;
    }

    private void prepareForEditing() {
        // Make sure edit form has latest data
        firstNameEditText.setText(sessionManager.getFirstName());
        lastNameEditText.setText(sessionManager.getLastName());
        emergencyContactEditText.setText(sessionManager.getEmergencyContact());
        phoneEditTextView.setText(sessionManager.getSavedPhoneNumber());

        // Clear any previous error states
        clearErrors();

        // Reset unsaved changes flag
        hasUnsavedChanges = false;
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

                        // Update displayed data
                        fullNameTextView.setText(firstName + " " + lastName);
                        if (emergencyContactTextView != null) {
                            emergencyContactTextView.setText(formatPhoneNumber(emergencyContact));
                        }

                        // Reset unsaved changes flag
                        hasUnsavedChanges = false;

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

    private String formatPhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return "";

        // Format for display - ensure it has +91 prefix
        if (!phoneNumber.startsWith("+")) {
            return "+91 " + phoneNumber;
        } else if (phoneNumber.startsWith("+91")) {
            return phoneNumber;
        } else {
            return "+91 " + phoneNumber.substring(1);
        }
    }

    private void setEditMode(boolean isEdit) {
        isEditing = isEdit;

        // Simple visibility toggle without animations to avoid issues
        viewProfileCardView.setVisibility(isEdit ? View.GONE : View.VISIBLE);
        editProfileCardView.setVisibility(isEdit ? View.VISIBLE : View.GONE);

        if (isEdit) {
            // Focus on first name field
            firstNameEditText.requestFocus();
            showKeyboard(firstNameEditText);
        } else {
            // Hide keyboard
            hideKeyboard();
        }
    }

    private void showDiscardChangesDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Discard Changes")
                .setMessage("You have unsaved changes. Are you sure you want to discard them?")
                .setPositiveButton("Discard", (dialog, which) -> {
                    // Discard changes and revert to view mode
                    prepareForEditing(); // Reset form with original data
                    setEditMode(false);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    // Continue editing
                    dialog.dismiss();
                })
                .setCancelable(true)
                .show();
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

        if (cancelUpdateButton != null) {
            cancelUpdateButton.setEnabled(!isLoading);
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

    private void showKeyboard(View view) {
        if (view.requestFocus()) {
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
            }
        }
    }

    private void hideKeyboard() {
        View view = requireActivity().getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }
}