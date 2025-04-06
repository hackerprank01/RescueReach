package com.rescuereach.citizen.fragments;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import java.util.Date;
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

        try {
            // Clean the phone number to digits only
            String digitsOnly = phone.replaceAll("[^0-9]", "");

            // Log the extracted digits for debugging
            Log.d("ProfileFragment", "Formatting phone number - digits only: " + digitsOnly);

            // If already has country code (more than 10 digits), format properly
            if (digitsOnly.length() > 10) {
                // Extract the last 10 digits
                String last10Digits = digitsOnly.substring(digitsOnly.length() - 10);
                Log.d("ProfileFragment", "Formatting with existing prefix: +91" + last10Digits);
                return "+91" + last10Digits;
            }

            // Get last 10 digits or all if less than 10
            String formattedNumber = "+91" + digitsOnly;
            Log.d("ProfileFragment", "Formatted phone number: " + formattedNumber);
            return formattedNumber;
        } catch (Exception e) {
            Log.e("ProfileFragment", "Error formatting phone number", e);
            // Return original with prefix as fallback
            return "+91" + phone;
        }
    }

    private String extractTenDigits(String phone) {
        if (phone == null || phone.isEmpty()) {
            return "";
        }

        try {
            // Extract only digits
            String digitsOnly = phone.replaceAll("[^0-9]", "");

            // Handle different formats
            if (digitsOnly.startsWith("91") && digitsOnly.length() > 10) {
                // Remove country code if present
                digitsOnly = digitsOnly.substring(2);
            }

            // Get last 10 digits (or all if less than 10)
            if (digitsOnly.length() > 10) {
                return digitsOnly.substring(digitsOnly.length() - 10);
            } else {
                return digitsOnly;
            }
        } catch (Exception e) {
            Log.e("ProfileFragment", "Error extracting phone digits", e);
            return phone; // Return original as fallback
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
//        View view = getView();
//        if (view != null) {
//            // Use the view variable instead of requireView() which can crash
//            View privacySettings = view.findViewById(R.id.layout_privacy_settings);
//            if (privacySettings != null) {
//                privacySettings.setOnClickListener(v ->
//                        startActivity(new Intent(requireContext(), PrivacySettingsActivity.class)));
//            }
//
//            View appearanceSettings = view.findViewById(R.id.layout_appearance_settings);
//            if (appearanceSettings != null) {
//                appearanceSettings.setOnClickListener(v ->
//                        startActivity(new Intent(requireContext(), AppearanceSettingsActivity.class)));
//            }
//
//            View dataManagement = view.findViewById(R.id.layout_data_management);
//            if (dataManagement != null) {
//                dataManagement.setOnClickListener(v ->
//                        startActivity(new Intent(requireContext(), DataManagementActivity.class)));
//            }
//        }
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

// Update the updateUIWithFirebaseData method for correct field mapping

    private void updateUIWithFirebaseData(DocumentSnapshot document) {
        try {
            // Clear all fields first to prevent data mixing
            inputFullName.setText("");
            inputPhone.setText("");
            inputDob.setText("");
            dropdownGender.setText("", false);
            dropdownState.setText("", false);
            inputContactPhone.setText("");

            // Extract data with safe type handling
            String fullName = safeGetString(document, "fullName");
            String phoneNum = safeGetString(document, "phoneNumber");
            String gender = safeGetString(document, "gender");
            String state = safeGetString(document, "state");
            String emergencyContact = safeGetString(document, "emergencyContact");

            // Handle date of birth specially (could be Date, Timestamp or String)
            String dob = "";
            try {
                if (document.contains("dateOfBirth")) {
                    Object dateObj = document.get("dateOfBirth");
                    if (dateObj instanceof String) {
                        dob = (String) dateObj;
                    } else if (dateObj instanceof com.google.firebase.Timestamp) {
                        com.google.firebase.Timestamp timestamp = (com.google.firebase.Timestamp) dateObj;
                        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
                        dob = sdf.format(timestamp.toDate());
                    } else if (dateObj instanceof Date) {
                        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
                        dob = sdf.format((Date) dateObj);
                    } else if (dateObj != null) {
                        // Last resort, convert to string
                        dob = dateObj.toString();
                    }
                }
            } catch (Exception e) {
                Log.e("ProfileFragment", "Error parsing date of birth", e);
            }

            // Handle volunteer status
            Boolean volunteer = null;
            try {
                if (document.contains("isVolunteer")) {
                    volunteer = document.getBoolean("isVolunteer");
                }
            } catch (Exception e) {
                Log.e("ProfileFragment", "Error parsing volunteer status", e);
            }

            // Debug log
            Log.d("ProfileFragment", "Parsed fields - " +
                    "fullName: " + fullName +
                    ", phone: " + phoneNum +
                    ", dob: " + dob +
                    ", gender: " + gender +
                    ", state: " + state +
                    ", emergencyContact: " + emergencyContact +
                    ", volunteer: " + volunteer);

            // Update UI with null checks
            if (fullName != null && !fullName.isEmpty()) inputFullName.setText(fullName);
            if (phoneNum != null && !phoneNum.isEmpty()) inputPhone.setText(phoneNum);
            if (dob != null && !dob.isEmpty()) inputDob.setText(dob);
            if (gender != null && !gender.isEmpty()) dropdownGender.setText(gender, false);
            if (state != null && !state.isEmpty()) dropdownState.setText(state, false);

            // Format emergency contact properly
            if (emergencyContact != null && !emergencyContact.isEmpty()) {
                String digits = extractTenDigits(emergencyContact);
                inputContactPhone.setText(digits);
            }

            // Set volunteer status with null check
            isVolunteer = (volunteer != null) ? volunteer : false;
            initialVolunteerStatus = isVolunteer;
            switchVolunteer.setChecked(isVolunteer);

            // Save values to session
            updateSessionWithCurrentValues();

        } catch (Exception e) {
            Log.e("ProfileFragment", "Error updating UI with Firebase data", e);
            showError("Error showing profile data. Loading from local storage.");
            loadUserDataFromLocalSession();
        }
    }

    // Helper method to safely get strings
    private String safeGetString(DocumentSnapshot document, String fieldName) {
        try {
            if (document.contains(fieldName)) {
                Object value = document.get(fieldName);
                if (value != null) {
                    return value.toString();
                }
            }
        } catch (Exception e) {
            Log.e("ProfileFragment", "Error getting field: " + fieldName, e);
        }
        return "";
    }

    private boolean isValidGender(String gender) {
        String[] validGenders = {"Male", "Female", "Other", "Prefer not to say"};
        for (String validGender : validGenders) {
            if (validGender.equalsIgnoreCase(gender)) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidState(String state) {
        String[] states = {
                "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar",
                "Chhattisgarh", "Goa", "Gujarat", "Haryana", "Himachal Pradesh",
                "Jharkhand", "Karnataka", "Kerala", "Madhya Pradesh", "Maharashtra",
                "Manipur", "Meghalaya", "Mizoram", "Nagaland", "Odisha",
                "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana",
                "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal"
        };

        for (String validState : states) {
            if (validState.equalsIgnoreCase(state)) {
                return true;
            }
        }
        return false;
    }

    // Improve loadUserDataFromLocalSession to prevent wrong field mapping
    private void loadUserDataFromLocalSession() {
        try {
            // Clear all fields first to avoid data bleed-through
            inputFullName.setText("");
            inputPhone.setText("");
            inputDob.setText("");
            dropdownGender.setText("", false);
            dropdownState.setText("", false);
            inputContactPhone.setText("");

            // Load user data with explicit logging
            String fullName = sessionManager.getFullName();
            Log.d("ProfileFragment", "Session full name: " + fullName);
            if (fullName != null && !fullName.isEmpty()) {
                inputFullName.setText(fullName);
            }

            String phone = sessionManager.getSavedPhoneNumber();
            Log.d("ProfileFragment", "Session phone: " + phone);
            if (phone != null && !phone.isEmpty()) {
                inputPhone.setText(phone);
            }

            // Load other saved data
            String dob = sessionManager.getDateOfBirthString();
            Log.d("ProfileFragment", "Session DOB: " + dob);
            if (dob != null && !dob.isEmpty()) {
                inputDob.setText(dob);
            }

            String gender = sessionManager.getGender();
            Log.d("ProfileFragment", "Session gender: " + gender);
            if (gender != null && !gender.isEmpty()) {
                dropdownGender.setText(gender, false);
            }

            String state = sessionManager.getState();
            Log.d("ProfileFragment", "Session state: " + state);
            if (state != null && !state.isEmpty()) {
                dropdownState.setText(state, false);
            }

            // Emergency contact phone - extract 10 digits for display
            String contactPhone = sessionManager.getEmergencyContactPhone();
            Log.d("ProfileFragment", "Session emergency contact: " + contactPhone);
            if (contactPhone != null && !contactPhone.isEmpty()) {
                String digits = extractTenDigits(contactPhone);
                inputContactPhone.setText(digits);
            }

            // Volunteer status
            isVolunteer = sessionManager.isVolunteer();
            Log.d("ProfileFragment", "Session volunteer status: " + isVolunteer);
            initialVolunteerStatus = isVolunteer; // Store initial value
            switchVolunteer.setChecked(isVolunteer);
        } catch (Exception e) {
            Log.e("ProfileFragment", "Error loading data from session", e);
            showError("Error loading profile data from local storage");
        }
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

// Update the saveProfile method in ProfileFragment.java to properly handle navigation refresh

    private void saveProfile() {
        if (!validateInputs()) {
            return;
        }

        showLoading(true);

        // Track if volunteer status changed
        boolean volunteerStatusChanged = (initialVolunteerStatus != isVolunteer);
        if (volunteerStatusChanged) {
            Log.d("ProfileFragment", "Volunteer status changed from " +
                    initialVolunteerStatus + " to " + isVolunteer);
        }

        // Format the emergency contact with +91 prefix before saving
        String emergencyContactPhone = formatPhoneWithPrefix(inputContactPhone.getText().toString().trim());

        // *** Important: Update session BEFORE Firebase to ensure local state is consistent ***
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
        String gender = dropdownGender.getText().toString().trim();

        // First update navigation drawer based on session changes
        if (volunteerStatusChanged && getActivity() instanceof CitizenMainActivity) {
            // Initial refresh based on session changes
            Log.d("ProfileFragment", "Refreshing navigation drawer - initial");
            ((CitizenMainActivity) getActivity()).refreshNavigationDrawer();
        }

        // Now update Firebase data
        updateFirestoreProfile(userId, phoneNumber, fullName, gender, state, emergencyContactPhone, volunteerStatusChanged);
    }

    private void updateFirestoreProfile(String userId, String phoneNumber, String fullName, String state, String gender, String emergencyContactPhone, boolean volunteerStatusChanged) {
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
                lastName = "";
            }
        }

        // Prepare update map with all required fields
        Map<String, Object> updates = new HashMap<>();
        updates.put("fullName", fullName);
        updates.put("firstName", firstName);
        updates.put("lastName", lastName);
        updates.put("userId", userId);
        updates.put("state", dropdownGender.getText().toString());
        updates.put("gender", dropdownGender.getText().toString());
        updates.put("emergencyContact", emergencyContactPhone);
        updates.put("isVolunteer", isVolunteer);

        // Create separate document ID lookup and update operations
        db.collection("users")
                .whereEqualTo("phoneNumber", phoneNumber)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        // Get the document ID
                        String documentId = querySnapshot.getDocuments().get(0).getId();

                        try {
                            // Now update the document using its ID with transaction for better consistency
                            db.runTransaction(transaction -> {
                                transaction.update(db.collection("users").document(documentId), updates);
                                return null;
                            }).addOnSuccessListener(aVoid -> {
                                // Update realtime database after Firestore success
                                updateRealtimeDatabase(userId, fullName, state, emergencyContactPhone, volunteerStatusChanged);
                            }).addOnFailureListener(e -> {
                                // If transaction fails, try normal update
                                db.collection("users")
                                        .document(documentId)
                                        .update(updates)
                                        .addOnSuccessListener(aVoid -> {
                                            updateRealtimeDatabase(userId, fullName, state, emergencyContactPhone, volunteerStatusChanged);
                                        })
                                        .addOnFailureListener(e2 -> {
                                            showError("Failed to save profile: " + e2.getMessage());
                                            showLoading(false);
                                        });
                            });
                        } catch (Exception e) {
                            // Handle any transaction exceptions
                            showError("Error updating profile: " + e.getMessage());
                            showLoading(false);
                        }
                    } else {
                        // User document not found - create it
                        try {
                            updates.put("phoneNumber", phoneNumber);
                            updates.put("createdAt", new Date());

                            db.collection("users").document().set(updates)
                                    .addOnSuccessListener(aVoid -> {
                                        updateRealtimeDatabase(userId, fullName, state, emergencyContactPhone, volunteerStatusChanged);
                                    })
                                    .addOnFailureListener(e -> {
                                        showError("Error creating user profile: " + e.getMessage());
                                        showLoading(false);
                                    });
                        } catch (Exception e) {
                            showError("Error creating profile: " + e.getMessage());
                            showLoading(false);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    showError("Error finding user profile: " + e.getMessage());
                    showLoading(false);
                });
    }

    private void updateRealtimeDatabase(String userId, String fullName, String state, String emergencyContactPhone, boolean volunteerStatusChanged) {
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
        updates.put("gender", dropdownGender.getText().toString()); // Add gender explicitly
        updates.put("state",  dropdownGender.getText().toString());
        updates.put("emergencyContact", emergencyContactPhone);
        updates.put("isVolunteer", isVolunteer);
        updates.put("userId", userId);  // Keep userId as a field for reference

        // Use phone number as the document reference instead of userId
        rtDatabase.child("users").child(phoneKey).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    // Show success message
                    Toast.makeText(requireContext(), R.string.profile_saved, Toast.LENGTH_SHORT).show();

                    // Force the navigation drawer to refresh
                    forceNavigationDrawerRefresh();

                    setEditMode(false);
                    showLoading(false);
                })
                .addOnFailureListener(e -> {
                    showError("Failed to save profile to Realtime Database: " + e.getMessage());

                    // Force the navigation drawer to refresh anyway
                    forceNavigationDrawerRefresh();

                    setEditMode(false);
                    showLoading(false);
                });
    }


    // Add a new method to force navigation drawer refresh with a slight delay
    private void forceNavigationDrawerRefresh() {
        if (getActivity() instanceof CitizenMainActivity) {
            // First refresh
            ((CitizenMainActivity) getActivity()).refreshNavigationDrawer();

            // Second refresh with delay to ensure updates are applied
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (isAdded() && getActivity() instanceof CitizenMainActivity) {
                    ((CitizenMainActivity) getActivity()).refreshNavigationDrawer();
                }
            }, 500); // 500ms delay
        }
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