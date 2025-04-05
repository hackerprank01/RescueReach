package com.rescuereach.citizen.fragments;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.rescuereach.R;
import com.rescuereach.citizen.CitizenMainActivity;
import com.rescuereach.citizen.settings.PrivacySettingsActivity;
import com.rescuereach.citizen.settings.AppearanceSettingsActivity;
import com.rescuereach.citizen.settings.DataManagementActivity;
import com.rescuereach.service.auth.UserSessionManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ProfileFragment extends Fragment {

    // UI components
    private TextInputEditText inputFullName, inputPhone, inputDob;
    private TextInputEditText inputContactPhone;
    private AutoCompleteTextView dropdownGender, dropdownState;
    private SwitchMaterial switchVolunteer;
    private MaterialButton btnEditProfile, btnSaveProfile, btnCancelEdit;
    private CircularProgressIndicator progressIndicator;

    // Input layouts (for validation)
    private TextInputLayout inputLayoutFullName, inputLayoutDob;
    private TextInputLayout inputLayoutContactPhone;
    private TextInputLayout inputLayoutGender, inputLayoutState;

    // Data
    private UserSessionManager sessionManager;
    private final Calendar calendar = Calendar.getInstance();
    private boolean isVolunteer = false;
    private boolean isEditMode = false;

    // Firebase
    private FirebaseFirestore db;

    private static final int CONTACTS_PERMISSION_CODE = 100;
    private DatabaseReference rtDatabase;
    private boolean initialVolunteerStatus = false;

    // Contact picker
    private final ActivityResultLauncher<Intent> contactPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == android.app.Activity.RESULT_OK) {
                            Intent data = result.getData();
                            if (data != null) {
                                Uri contactUri = data.getData();
                                if (contactUri != null) {
                                    retrieveContactDetails(contactUri);
                                }
                            }
                        }
                    });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sessionManager = UserSessionManager.getInstance(requireContext());
        db = FirebaseFirestore.getInstance();
        rtDatabase = FirebaseDatabase.getInstance().getReference();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize UI components
        initUI(view);

        // Set up initial display mode (view mode)
        setEditMode(false);

        // Load user data
        loadUserDataFromFirebase();

        // Set up listeners
        setupListeners();
    }

    private void initUI(View view) {
        // Personal information
        inputFullName = view.findViewById(R.id.input_full_name);
        inputPhone = view.findViewById(R.id.input_phone);
        inputDob = view.findViewById(R.id.input_dob);
        dropdownGender = view.findViewById(R.id.dropdown_gender);
        dropdownState = view.findViewById(R.id.dropdown_state);

        // Emergency contact - only keeping the phone field
        inputContactPhone = view.findViewById(R.id.input_contact_phone);

        // Volunteer status
        switchVolunteer = view.findViewById(R.id.switch_volunteer);

        // Input layouts (for validation)
        inputLayoutFullName = view.findViewById(R.id.input_layout_full_name);
        inputLayoutDob = view.findViewById(R.id.input_layout_dob);
        inputLayoutContactPhone = view.findViewById(R.id.input_layout_contact_phone);
        inputLayoutGender = view.findViewById(R.id.input_layout_gender);
        inputLayoutState = view.findViewById(R.id.input_layout_state);

        // Buttons
        btnEditProfile = view.findViewById(R.id.btn_edit_profile);
        btnSaveProfile = view.findViewById(R.id.btn_save_profile);
        btnCancelEdit = view.findViewById(R.id.btn_cancel_edit);

        // Progress indicator
        progressIndicator = view.findViewById(R.id.progress_indicator);

        // Setup dropdowns
        setupDropdowns();
    }

    private String formatPhoneWithPrefix(String phone) {
        if (phone == null || phone.isEmpty()) {
            return "";
        }

        // Clean the phone number to digits only
        String digitsOnly = phone.replaceAll("[^0-9]", "");

        // If already has country code, use as is
        if (digitsOnly.length() > 10) {
            return "+91" + digitsOnly.substring(digitsOnly.length() - 10);
        }

        // Get last 10 digits or all if less than 10
        int startIndex = Math.max(0, digitsOnly.length() - 10);
        String lastTenDigits = digitsOnly.substring(startIndex);

        return "+91" + lastTenDigits;
    }

    private String extractTenDigits(String phone) {
        if (phone == null || phone.isEmpty()) {
            return "";
        }

        try {
            // Extract only digits
            String digitsOnly = phone.replaceAll("[^0-9]", "");

            // If has country code (more than 10 digits), remove it
            if (digitsOnly.length() > 10) {
                return digitsOnly.substring(digitsOnly.length() - 10);
            }

            // Get last 10 digits or all if less than 10
            int startIndex = Math.max(0, digitsOnly.length() - 10);
            return digitsOnly.substring(startIndex);
        } catch (Exception e) {
            Log.e("ProfileFragment", "Error extracting phone digits: " + e.getMessage(), e);
            // Return original as fallback
            return phone;
        }
    }
    private void setupDropdowns() {
        // Gender dropdown
        String[] genders = new String[] {"Male", "Female", "Other", "Prefer not to say"};
        ArrayAdapter<String> genderAdapter = new ArrayAdapter<>(
                requireContext(), R.layout.dropdown_item, genders);
        dropdownGender.setAdapter(genderAdapter);

        // State dropdown
        String[] states = new String[] {
                "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar",
                "Chhattisgarh", "Goa", "Gujarat", "Haryana", "Himachal Pradesh",
                "Jharkhand", "Karnataka", "Kerala", "Madhya Pradesh", "Maharashtra",
                "Manipur", "Meghalaya", "Mizoram", "Nagaland", "Odisha",
                "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana",
                "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal"
        };
        ArrayAdapter<String> stateAdapter = new ArrayAdapter<>(
                requireContext(), R.layout.dropdown_item, states);
        dropdownState.setAdapter(stateAdapter);
    }

// Fix the setupListeners method to use requireView() instead of view:

    private void setupListeners() {
        // Edit profile button
        btnEditProfile.setOnClickListener(v -> setEditMode(true));

        // Save profile button
        btnSaveProfile.setOnClickListener(v -> saveProfile());

        // Cancel edit button
        btnCancelEdit.setOnClickListener(v -> {
            // Reload user data and exit edit mode
            loadUserDataFromLocalSession();
            setEditMode(false);
        });

        // Date of birth picker
        inputLayoutDob.setEndIconOnClickListener(v -> showDatePicker());
        inputDob.setOnClickListener(v -> {
            if (isEditMode) {
                showDatePicker();
            }
        });

        // Contact picker
        inputLayoutContactPhone.setEndIconOnClickListener(v -> {
            if (isEditMode) {
                checkContactPermissionAndOpen();
            }
        });

        // Volunteer switch
        switchVolunteer.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isEditMode) {
                isVolunteer = isChecked;
            } else {
                // Reset to original value if not in edit mode
                switchVolunteer.setChecked(isVolunteer);
            }
        });

        // Settings click listeners
        View view = getView();
        if (view != null) {
            // Use the view variable instead of requireView() which can crash
            View privacySettings = view.findViewById(R.id.layout_privacy_settings);
            if (privacySettings != null) {
                privacySettings.setOnClickListener(v ->
                        startActivity(new Intent(requireContext(), PrivacySettingsActivity.class)));
            }

            View appearanceSettings = view.findViewById(R.id.layout_appearance_settings);
            if (appearanceSettings != null) {
                appearanceSettings.setOnClickListener(v ->
                        startActivity(new Intent(requireContext(), AppearanceSettingsActivity.class)));
            }

            View dataManagement = view.findViewById(R.id.layout_data_management);
            if (dataManagement != null) {
                dataManagement.setOnClickListener(v ->
                        startActivity(new Intent(requireContext(), DataManagementActivity.class)));
            }
        }
    }

    private void loadUserDataFromFirebase() {
        showLoading(true);

        String phoneNumber = sessionManager.getSavedPhoneNumber();
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            showError("No phone number found. Please login again.");
            showLoading(false);
            return;
        }

        // First, try to load from Firebase
        db.collection("users")
                .whereEqualTo("phoneNumber", phoneNumber)
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        DocumentSnapshot document = task.getResult().getDocuments().get(0);
                        updateUIWithFirebaseData(document);
                    } else {
                        // If Firebase fails, fall back to local session data
                        loadUserDataFromLocalSession();
                    }
                    showLoading(false);
                })
                .addOnFailureListener(e -> {
                    showError("Failed to load profile: " + e.getMessage());
                    loadUserDataFromLocalSession();
                    showLoading(false);
                });
    }

    private void updateUIWithFirebaseData(DocumentSnapshot document) {
        try {
            // Extract data from Firestore
            String fullName = document.getString("fullName");
            String phoneNum = document.getString("phoneNumber");
            String dob = document.getString("dateOfBirth");
            String gender = document.getString("gender");
            String state = document.getString("state");
            Boolean volunteer = document.getBoolean("isVolunteer");

            // Emergency contact details - add additional null check and formatting
            String emergencyContact = document.getString("emergencyContact");
            if (emergencyContact != null && !emergencyContact.isEmpty()) {
                try {
                    // Format and display emergency contact
                    inputContactPhone.setText(extractTenDigits(emergencyContact));
                } catch (Exception e) {
                    Log.e("ProfileFragment", "Error formatting emergency contact: " + e.getMessage());
                    // Use raw value as fallback
                    inputContactPhone.setText(emergencyContact);
                }
            }

            // Update UI with null checks for all fields
            if (fullName != null && !fullName.isEmpty()) inputFullName.setText(fullName);
            if (phoneNum != null && !phoneNum.isEmpty()) inputPhone.setText(phoneNum);
            if (dob != null && !dob.isEmpty()) inputDob.setText(dob);
            if (gender != null && !gender.isEmpty()) dropdownGender.setText(gender, false);
            if (state != null && !state.isEmpty()) dropdownState.setText(state, false);

            // Handle volunteer status with null check
            isVolunteer = volunteer != null ? volunteer : false;
            initialVolunteerStatus = isVolunteer;
            switchVolunteer.setChecked(isVolunteer);

            // Save to local session
            updateSessionWithCurrentValues();

        } catch (Exception e) {
            Log.e("ProfileFragment", "Error parsing profile data: " + e.getMessage(), e);
            showError("Error loading profile data: " + e.getMessage());
            // Load from session as fallback
            loadUserDataFromLocalSession();
        }
    }


    private void loadUserDataFromLocalSession() {
        // Load user data from session manager
        inputFullName.setText(sessionManager.getFullName());
        inputPhone.setText(sessionManager.getSavedPhoneNumber());

        // Load other saved data
        String dob = sessionManager.getDateOfBirthString();
        if (dob != null && !dob.isEmpty()) {
            inputDob.setText(dob);
        }

        String gender = sessionManager.getGender();
        if (gender != null && !gender.isEmpty()) {
            dropdownGender.setText(gender, false);
        }

        String state = sessionManager.getState();
        if (state != null && !state.isEmpty()) {
            dropdownState.setText(state, false);
        }

        // Emergency contact phone - extract 10 digits for display
        String contactPhone = sessionManager.getEmergencyContactPhone();
        if (contactPhone != null && !contactPhone.isEmpty()) {
            inputContactPhone.setText(extractTenDigits(contactPhone));
        }

        // Volunteer status
        isVolunteer = sessionManager.isVolunteer();
        initialVolunteerStatus = isVolunteer; // Store initial value
        switchVolunteer.setChecked(isVolunteer);
    }

    private void showDatePicker() {
        if (!isEditMode) return;

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    updateDateInView();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));

        // Set max date to today (no future dates)
        datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis());

        datePickerDialog.show();
    }

    private void updateDateInView() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        inputDob.setText(sdf.format(calendar.getTime()));
    }

    private void openContactPicker() {
        if (!isEditMode) return;

        Intent contactPickerIntent = new Intent(Intent.ACTION_PICK,
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        contactPickerLauncher.launch(contactPickerIntent);
    }

    private void retrieveContactDetails(Uri contactUri) {
        String[] projection = new String[] {
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };

        Cursor cursor = requireContext().getContentResolver().query(
                contactUri, projection, null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            int numberIndex = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.NUMBER);

            String phoneNumber = cursor.getString(numberIndex);
            // Extract 10 digits and display in the input field
            inputContactPhone.setText(extractTenDigits(phoneNumber));

            cursor.close();
        }
    }

    private boolean validateInputs() {
        boolean isValid = true;

        // Validate full name
        if (TextUtils.isEmpty(inputFullName.getText())) {
            inputLayoutFullName.setError(getString(R.string.error_required_field));
            isValid = false;
        } else {
            inputLayoutFullName.setError(null);
        }

        // Emergency contact phone is required
        String phone = inputContactPhone.getText() != null ?
                inputContactPhone.getText().toString() : "";
        if (TextUtils.isEmpty(phone)) {
            inputLayoutContactPhone.setError(getString(R.string.error_required_field));
            isValid = false;
        } else if (!isValidPhoneNumber(phone)) {
            inputLayoutContactPhone.setError(getString(R.string.error_invalid_phone));
            isValid = false;
        } else {
            inputLayoutContactPhone.setError(null);
        }

        // State is required
        if (TextUtils.isEmpty(dropdownState.getText())) {
            inputLayoutState.setError(getString(R.string.error_required_field));
            isValid = false;
        } else {
            inputLayoutState.setError(null);
        }

        return isValid;
    }

    private boolean isValidPhoneNumber(String phone) {
        // Basic validation - more complex validation would be needed in a real app
        return phone.length() >= 10;
    }

    private void saveProfile() {
        if (!validateInputs()) {
            return;
        }

        showLoading(true);

        // Store initial value to check if it changed
        boolean volunteerStatusChanged = (initialVolunteerStatus != isVolunteer);

        // Format the emergency contact with +91 prefix before saving
        String emergencyContactPhone = formatPhoneWithPrefix(inputContactPhone.getText().toString().trim());

        // Update session with current values including formatted phone
        sessionManager.setFullName(inputFullName.getText().toString().trim());
        sessionManager.setState(dropdownState.getText().toString().trim());
        sessionManager.setGender(dropdownGender.getText().toString().trim());
        sessionManager.setDateOfBirth(inputDob.getText().toString().trim());
        sessionManager.setEmergencyContactPhone(emergencyContactPhone);
        sessionManager.setVolunteer(isVolunteer);

        // Get the current user's phone number and user ID
        String phoneNumber = sessionManager.getSavedPhoneNumber();
        String userId = sessionManager.getUserId();

        if (phoneNumber == null || userId == null) {
            showError("Phone number or user ID is missing");
            showLoading(false);
            return;
        }

        // Prepare data to update
        String fullName = inputFullName.getText().toString().trim();
        String state = dropdownState.getText().toString().trim();

        // Step 1: Update Firestore

        updateFirestoreProfile(userId, phoneNumber, fullName, state, emergencyContactPhone,volunteerStatusChanged );
    }

    // New method to update Firestore
    private void updateFirestoreProfile(String userId, String phoneNumber,
                                        String fullName, String state, String emergencyContactPhone,
                                        boolean volunteerStatusChanged) {
        // Extract first and last name from full name - same as in updateRealtimeDatabase
        String firstName;
        String lastName;

        if (fullName != null && !fullName.isEmpty()) {
            int spaceIndex = fullName.indexOf(' ');
            if (spaceIndex > 0) {
                firstName = fullName.substring(0, spaceIndex);
                lastName = fullName.substring(spaceIndex + 1);
            } else {
                lastName = "";
                firstName = fullName;
            }
        } else {
            lastName = "";
            firstName = "";
        }

        db.collection("users")
                .whereEqualTo("phoneNumber", phoneNumber)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        // Get the document ID
                        String documentId = querySnapshot.getDocuments().get(0).getId();

                        // Now update the document using its ID
                        db.collection("users")
                                .document(documentId)
                                .update(
                                        "fullName", fullName,
                                        "firstName", firstName, // Add firstName
                                        "lastName", lastName,   // Add lastName
                                        "userId", userId,       // Always include userId
                                        "state", state,
                                        "emergencyContact", emergencyContactPhone,
                                        "isVolunteer", isVolunteer
                                )
                                .addOnSuccessListener(aVoid -> {
                                    // Now update Realtime Database after Firestore success
                                    updateRealtimeDatabase(userId, fullName, state, emergencyContactPhone, volunteerStatusChanged);
                                })
                                .addOnFailureListener(e -> {
                                    showError("Failed to save profile to Firestore: " + e.getMessage());
                                    showLoading(false);
                                });
                    } else {
                        showError("User profile not found in Firestore");
                        showLoading(false);
                    }
                })
                .addOnFailureListener(e -> {
                    showError("Failed to find user profile: " + e.getMessage());
                    showLoading(false);
                });
    }

    // New method to update Realtime Database
    private void updateRealtimeDatabase(String userId, String fullName,
                                        String state, String emergencyContactPhone,
                                        boolean volunteerStatusChanged) {
        // Extract first and last name from full name
        String firstName = "";
        String lastName = "";

        if (fullName != null && !fullName.isEmpty()) {
            int spaceIndex = fullName.indexOf(' ');
            if (spaceIndex > 0) {
                firstName = fullName.substring(0, spaceIndex);
                lastName = fullName.substring(spaceIndex + 1);
            } else {
                firstName = fullName;
            }
        }

        // Get the phone number for document reference
        String phoneNumber = sessionManager.getSavedPhoneNumber();
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            showError("Phone number is missing for database update");
            showLoading(false);
            return;
        }

        // Format phone number to be used as a key (remove special characters)
        String phoneKey = phoneNumber.replaceAll("[^\\d]", "");

        Map<String, Object> updates = new HashMap<>();
        updates.put("fullName", fullName);
        updates.put("firstName", firstName);
        updates.put("lastName", lastName);
        updates.put("state", state);
        updates.put("emergencyContact", emergencyContactPhone);
        updates.put("isVolunteer", isVolunteer);
        updates.put("userId", userId);  // Keep userId as a field for reference

        // Use phone number as the document reference instead of userId
        rtDatabase.child("users").child(phoneKey).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    // Show success message
                    Toast.makeText(requireContext(), R.string.profile_saved, Toast.LENGTH_SHORT).show();

                    // If volunteer status changed, refresh navigation drawer
                    if (volunteerStatusChanged) {
                        refreshNavigationDrawer();
                    }

                    setEditMode(false);
                    showLoading(false);
                })
                .addOnFailureListener(e -> {
                    showError("Failed to save profile to Realtime Database: " + e.getMessage());
                    // Even if Realtime DB fails, Firestore was updated successfully

                    // If volunteer status changed, refresh navigation drawer 
                    if (volunteerStatusChanged) {
                        refreshNavigationDrawer();
                    }

                    setEditMode(false);
                    showLoading(false);
                });
    }

    private void refreshNavigationDrawer() {
        // Request the main activity to refresh its navigation drawer
        if (getActivity() instanceof CitizenMainActivity) {
            ((CitizenMainActivity) getActivity()).refreshNavigationDrawer();
        }
    }

    private void updateSessionWithCurrentValues() {
        // Update session with current values
        sessionManager.setFullName(inputFullName.getText().toString().trim());
        sessionManager.setState(dropdownState.getText().toString().trim());
        sessionManager.setGender(dropdownGender.getText().toString().trim());
        sessionManager.setDateOfBirth(inputDob.getText().toString().trim());

        // Emergency contact phone only
        sessionManager.setEmergencyContactPhone(inputContactPhone.getText().toString().trim());

        // Volunteer status
        sessionManager.setVolunteer(isVolunteer);
    }

    private void setEditMode(boolean editMode) {
        isEditMode = editMode;

        // Toggle button visibility
        btnEditProfile.setVisibility(editMode ? View.GONE : View.VISIBLE);
        btnSaveProfile.setVisibility(editMode ? View.VISIBLE : View.GONE);
        btnCancelEdit.setVisibility(editMode ? View.VISIBLE : View.GONE);

        // Toggle editability of fields
        // Only allow editing of full name, state, emergency contact, and volunteer status
        inputFullName.setEnabled(editMode);
        inputPhone.setEnabled(false); // Never editable
        inputDob.setEnabled(false);   // Never editable
        dropdownGender.setEnabled(false); // Never editable
        dropdownState.setEnabled(editMode);

        // Emergency contact phone
        inputContactPhone.setEnabled(editMode);

        // Volunteer status
        switchVolunteer.setEnabled(editMode);

        // Update input layouts
        updateInputLayoutsForEditMode(editMode);
    }

    private void updateInputLayoutsForEditMode(boolean editMode) {
        // Change end icons based on edit mode
        inputLayoutFullName.setEndIconMode(editMode ? TextInputLayout.END_ICON_CLEAR_TEXT : TextInputLayout.END_ICON_NONE);
        inputLayoutDob.setEndIconMode(TextInputLayout.END_ICON_NONE); // DOB not editable

        // Set contact picker icon when in edit mode
        if (editMode) {
            inputLayoutContactPhone.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
            inputLayoutContactPhone.setEndIconDrawable(R.drawable.ic_contacts);
            inputLayoutContactPhone.setEndIconOnClickListener(v -> checkContactPermissionAndOpen());
        } else {
            inputLayoutContactPhone.setEndIconMode(TextInputLayout.END_ICON_NONE);
        }

        // Update dropdown layouts
        inputLayoutGender.setEnabled(false); // Never editable
        inputLayoutState.setEnabled(editMode);
    }

    private void checkContactPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission not granted, request it
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.READ_CONTACTS},
                    CONTACTS_PERMISSION_CODE);
        } else {
            // Permission already granted, open contact picker
            openContactPicker();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == CONTACTS_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, open contact picker
                openContactPicker();
            } else {
                // Permission denied
                Toast.makeText(requireContext(),
                        "Contacts permission is needed to select contacts",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }


    private void showLoading(boolean isLoading) {
        progressIndicator.setVisibility(isLoading ? View.VISIBLE : View.GONE);

        // Disable interaction while loading
        btnEditProfile.setEnabled(!isLoading);
        btnSaveProfile.setEnabled(!isLoading);
        btnCancelEdit.setEnabled(!isLoading);
    }

    private void showError(String message) {
        if (isAdded() && getContext() != null) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
        }
    }

    private void showNotImplementedToast(String feature) {
        Toast.makeText(requireContext(),
                getString(R.string.feature_not_implemented, feature),
                Toast.LENGTH_SHORT).show();
    }
}