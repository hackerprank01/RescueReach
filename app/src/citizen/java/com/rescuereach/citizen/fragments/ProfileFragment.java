package com.rescuereach.citizen.fragments;

import android.Manifest;
import android.app.Activity;
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
//import com.rescuereach.citizen.settings.PrivacySettingsActivity;
//import com.rescuereach.citizen.settings.AppearanceSettingsActivity;
//import com.rescuereach.citizen.settings.DataManagementActivity;
import com.rescuereach.service.auth.UserSessionManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ProfileFragment extends Fragment {

    private static final String TAG = "ProfileFragment";

    private static final int CONTACTS_PERMISSION_CODE = 103;
    private static final int CONTACT_PICK_CODE = 104;


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
    private boolean initialVolunteerStatus = false;

    // Firebase
    private FirebaseFirestore db;
    private DatabaseReference rtDatabase;

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

        // Find views
        inputLayoutContactPhone = view.findViewById(R.id.input_layout_contact_phone);
        inputContactPhone = view.findViewById(R.id.input_contact_phone);

        // Initialize UI components
        initUI(view);

        // Set up initial display mode (view mode)
        setEditMode(false);

        // Load user data
        loadUserDataFromFirebase();

        // Set up listeners
        setupListeners();

        inputLayoutContactPhone.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        inputLayoutContactPhone.setEndIconDrawable(R.drawable.ic_contacts);
        inputLayoutContactPhone.setEndIconOnClickListener(v -> {
            if (inputContactPhone.isEnabled()) { // Only when in edit mode
                requestContactPermission();
            }
        });
    }

    private void requestContactPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, CONTACTS_PERMISSION_CODE);
        } else {
            openContactPicker();
        }
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

    private void setupDropdowns() {
        // Gender dropdown
        String[] genders = new String[] {"Male", "Female", "Other", "Prefer not to say"};
        ArrayAdapter<String> genderAdapter = new ArrayAdapter<>(
                requireContext(), R.layout.dropdown_item, genders);
        dropdownGender.setAdapter(genderAdapter);

        // State dropdown
        String[] states = getResources().getStringArray(R.array.indian_states);
        ArrayAdapter<String> stateAdapter = new ArrayAdapter<>(
                requireContext(), R.layout.dropdown_item, states);
        dropdownState.setAdapter(stateAdapter);
    }

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

//        // Settings click listeners
//        View view = getView();
//        if (view != null) {
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

    private void updateUIWithFirebaseData(DocumentSnapshot document) {
        try {
            // Clear all fields first to prevent data mixing
            clearAllFields();

            // Extract data with safe type handling
            String fullName = safeGetString(document, "fullName");
            String phoneNum = safeGetString(document, "phoneNumber");
            String gender = safeGetString(document, "gender");
            String state = safeGetString(document, "state");
            String emergencyContact = safeGetString(document, "emergencyContact");

            // Handle date of birth specially (could be Date, Timestamp or String)
            String dob = getFormattedDateFromDocument(document);

            // Handle volunteer status
            Boolean volunteer = getVolunteerStatusFromDocument(document);

            // Debug log
            Log.d(TAG, "Parsed fields - " +
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
            Log.e(TAG, "Error updating UI with Firebase data", e);
            showError("Error showing profile data. Loading from local storage.");
            loadUserDataFromLocalSession();
        }
    }

    private void clearAllFields() {
        inputFullName.setText("");
        inputPhone.setText("");
        inputDob.setText("");
        dropdownGender.setText("", false);
        dropdownState.setText("", false);
        inputContactPhone.setText("");
    }

    private String getFormattedDateFromDocument(DocumentSnapshot document) {
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
            Log.e(TAG, "Error parsing date of birth", e);
        }
        return dob;
    }

    private Boolean getVolunteerStatusFromDocument(DocumentSnapshot document) {
        try {
            if (document.contains("isVolunteer")) {
                return document.getBoolean("isVolunteer");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing volunteer status", e);
        }
        return false;
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
            Log.e(TAG, "Error getting field: " + fieldName, e);
        }
        return "";
    }

    private void loadUserDataFromLocalSession() {
        try {
            // Clear all fields first to avoid data bleed-through
            clearAllFields();

            // Load user data with explicit logging
            String fullName = sessionManager.getFullName();
            Log.d(TAG, "Session full name: " + fullName);
            if (fullName != null && !fullName.isEmpty()) {
                inputFullName.setText(fullName);
            }

            String phone = sessionManager.getSavedPhoneNumber();
            Log.d(TAG, "Session phone: " + phone);
            if (phone != null && !phone.isEmpty()) {
                inputPhone.setText(phone);
            }

            // Load other saved data
            String dob = sessionManager.getDateOfBirthString();
            Log.d(TAG, "Session DOB: " + dob);
            if (dob != null && !dob.isEmpty()) {
                inputDob.setText(dob);
            }

            String gender = sessionManager.getGender();
            Log.d(TAG, "Session gender: " + gender);
            if (gender != null && !gender.isEmpty()) {
                dropdownGender.setText(gender, false);
            }

            String state = sessionManager.getState();
            Log.d(TAG, "Session state: " + state);
            if (state != null && !state.isEmpty()) {
                dropdownState.setText(state, false);
            }

            // Emergency contact phone - extract 10 digits for display
            String contactPhone = sessionManager.getEmergencyContactPhone();
            Log.d(TAG, "Session emergency contact: " + contactPhone);
            if (contactPhone != null && !contactPhone.isEmpty()) {
                String digits = extractTenDigits(contactPhone);
                inputContactPhone.setText(digits);
            }

            // Volunteer status
            isVolunteer = sessionManager.isVolunteer();
            Log.d(TAG, "Session volunteer status: " + isVolunteer);
            initialVolunteerStatus = isVolunteer; // Store initial value
            switchVolunteer.setChecked(isVolunteer);
        } catch (Exception e) {
            Log.e(TAG, "Error loading data from session", e);
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
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        startActivityForResult(intent, CONTACT_PICK_CODE);
    }

    private void retrieveContactDetails(Uri contactUri) {
        String[] projection = new String[] {
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };

        try (Cursor cursor = requireContext().getContentResolver().query(
                contactUri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int numberIndex = cursor.getColumnIndex(
                        ContactsContract.CommonDataKinds.Phone.NUMBER);

                String phoneNumber = cursor.getString(numberIndex);
                // Extract 10 digits and display in the input field
                inputContactPhone.setText(extractTenDigits(phoneNumber));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving contact", e);
            showError("Failed to retrieve contact information");
        }
    }

    private boolean validateInputs() {
        boolean isValid = true;

        // Validate full name
        if (TextUtils.isEmpty(inputFullName.getText())) {
            inputLayoutFullName.setError(getString(R.string.error_required_field));
            isValid = false;
        } else if (inputFullName.getText().toString().trim().length() < 3) {
            inputLayoutFullName.setError(getString(R.string.error_name_too_short));
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
        } else if (isSameAsUserPhone(phone)) {
            inputLayoutContactPhone.setError(getString(R.string.error_emergency_same_as_user));
            isValid = false;
        } else {
            inputLayoutContactPhone.setError(null);
        }

        // State is required
        if (TextUtils.isEmpty(dropdownState.getText())) {
            inputLayoutState.setError(getString(R.string.error_required_field));
            isValid = false;
        } else if (!isValidState(dropdownState.getText().toString())) {
            inputLayoutState.setError(getString(R.string.error_invalid_state));
            isValid = false;
        } else {
            inputLayoutState.setError(null);
        }

        return isValid;
    }

    private boolean isValidPhoneNumber(String phone) {
        // Basic validation for 10-digit Indian mobile number
        return phone.length() == 10 && phone.matches("[6-9]\\d{9}");
    }

    private boolean isSameAsUserPhone(String emergencyPhone) {
        String userPhone = sessionManager.getSavedPhoneNumber();
        if (userPhone == null) return false;

        String userDigits = extractTenDigits(userPhone);
        return userDigits.equals(emergencyPhone);
    }

    private boolean isValidState(String state) {
        if (state == null || state.isEmpty()) return false;

        String[] states = getResources().getStringArray(R.array.indian_states);
        for (String validState : states) {
            if (validState.equalsIgnoreCase(state)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Save profile data following the exact flowchart sequence
     */
    private void saveProfile() {
        // 1. Validate inputs
        if (!validateInputs()) {
            return;
        }

        // 2. Show loading and collect data
        showLoading(true);

        // 3. Collect data from UI fields
        String fullName = inputFullName.getText().toString().trim();
        String state = dropdownState.getText().toString().trim();
        String gender = dropdownGender.getText().toString().trim();
        String emergencyContactRaw = inputContactPhone.getText().toString().trim();

        // 4. Format emergency contact with +91 prefix
        String emergencyContact = formatPhoneWithPrefix(emergencyContactRaw);

        // 5. Track volunteer status change
        boolean volunteerStatusChanged = (initialVolunteerStatus != isVolunteer);

        // 6. Update session first
        updateSessionWithCurrentValues();

        // 7. Get phone number and user ID
        String phoneNumber = sessionManager.getSavedPhoneNumber();
        String userId = sessionManager.getUserId();

        if (phoneNumber == null || userId == null) {
            showError("Phone number or user ID is missing");
            showLoading(false);
            return;
        }

        // 8. If volunteer status changed, update navigation drawer
        if (volunteerStatusChanged && getActivity() instanceof CitizenMainActivity) {
            // Initial refresh based on session changes
            Log.d(TAG, "Refreshing navigation drawer - initial");
            ((CitizenMainActivity) getActivity()).refreshNavigationDrawer();
        }

        // 9. Update Firestore
        updateFirestoreProfile(userId, phoneNumber, fullName, gender, state, emergencyContact, volunteerStatusChanged);
    }

    private void updateFirestoreProfile(String userId, String phoneNumber, String fullName,
                                        String gender, String state, String emergencyContact,
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
                lastName = "";
            }
        }

        // Prepare update map with all required fields
        Map<String, Object> updates = new HashMap<>();
        updates.put("fullName", fullName);
        updates.put("firstName", firstName);
        updates.put("lastName", lastName);
        updates.put("userId", userId);
        updates.put("state", state);
        updates.put("gender", gender);
        updates.put("emergencyContact", emergencyContact);
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
                                updateRealtimeDatabase(userId, fullName, gender, state, emergencyContact, volunteerStatusChanged);
                            }).addOnFailureListener(e -> {
                                // If transaction fails, try normal update
                                db.collection("users")
                                        .document(documentId)
                                        .update(updates)
                                        .addOnSuccessListener(aVoid -> {
                                            updateRealtimeDatabase(userId, fullName, gender, state, emergencyContact, volunteerStatusChanged);
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
                                        updateRealtimeDatabase(userId, fullName, gender, state, emergencyContact, volunteerStatusChanged);
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

    private void updateRealtimeDatabase(String userId, String fullName, String gender,
                                        String state, String emergencyContact,
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
        updates.put("gender", gender);
        updates.put("state", state);
        updates.put("emergencyContact", emergencyContact);
        updates.put("isVolunteer", isVolunteer);
        updates.put("userId", userId);  // Keep userId as a field for reference

        // Use phone number as the document reference instead of userId
        rtDatabase.child("users").child(phoneKey).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    // Show success message
                    if (isAdded()) {
                        Toast.makeText(requireContext(), R.string.profile_saved, Toast.LENGTH_SHORT).show();
                    }

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

    private void forceNavigationDrawerRefresh() {
        if (getActivity() instanceof CitizenMainActivity) {
            // Immediate refresh
            ((CitizenMainActivity) getActivity()).refreshNavigationDrawer();

            // Delayed refresh to ensure updates are applied
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (isAdded() && getActivity() instanceof CitizenMainActivity) {
                    ((CitizenMainActivity) getActivity()).refreshNavigationDrawer();
                }
            }, 500); // 500ms delay
        }
    }

    private void updateSessionWithCurrentValues() {
        // Update session with current values
        sessionManager.setFullName(inputFullName.getText().toString().trim());
        sessionManager.setState(dropdownState.getText().toString().trim());
        sessionManager.setGender(dropdownGender.getText().toString().trim());
        sessionManager.setDateOfBirth(inputDob.getText().toString().trim());

        // Format and save emergency contact
        String formattedContact = formatPhoneWithPrefix(inputContactPhone.getText().toString().trim());
        sessionManager.setEmergencyContactPhone(formattedContact);

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
        inputFullName.setEnabled(editMode);
        inputPhone.setEnabled(false); // Never editable
        inputDob.setEnabled(false);   // Only editable via date picker
        dropdownGender.setEnabled(false); // Not editable in this version
        dropdownState.setEnabled(editMode);
        inputContactPhone.setEnabled(editMode);
        switchVolunteer.setEnabled(editMode);

        // Update input layouts
        updateInputLayoutsForEditMode(editMode);

        inputLayoutContactPhone.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
    }

    private void updateInputLayoutsForEditMode(boolean editMode) {
        // Change end icons based on edit mode
        inputLayoutFullName.setEndIconMode(editMode ? TextInputLayout.END_ICON_CLEAR_TEXT : TextInputLayout.END_ICON_NONE);
        inputLayoutDob.setEndIconMode(editMode ? TextInputLayout.END_ICON_CUSTOM : TextInputLayout.END_ICON_NONE);

        inputLayoutContactPhone.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        inputLayoutContactPhone.setEndIconDrawable(R.drawable.ic_contacts);

        // Update dropdown layouts
        inputLayoutGender.setEnabled(false); // Never editable
        inputLayoutState.setEnabled(editMode);
    }

    private void checkContactPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission not granted, request it
            requestPermissions(
                    new String[]{Manifest.permission.READ_CONTACTS},
                    CONTACTS_PERMISSION_CODE);
        } else {
            // Permission already granted, open contact picker
            openContactPicker();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == CONTACTS_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openContactPicker();
            } else {
                Toast.makeText(requireContext(), "Contact permission denied", Toast.LENGTH_SHORT).show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == CONTACT_PICK_CODE && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                Uri contactUri = data.getData();
                String[] projection = {ContactsContract.CommonDataKinds.Phone.NUMBER};

                try (Cursor cursor = requireActivity().getContentResolver().query(contactUri, projection, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                        String phoneNumber = cursor.getString(numberIndex);

                        // Remove the +91 prefix as the layout already adds it
                        if (phoneNumber.startsWith("+91")) {
                            phoneNumber = phoneNumber.substring(3);
                        }

                        inputContactPhone.setText(phoneNumber);
                    }
                } catch (Exception e) {
                    Log.e("ProfileFragment", "Error reading contact", e);
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void showLoading(boolean isLoading) {
        if (progressIndicator != null) {
            progressIndicator.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }

        // Disable interaction while loading
        if (btnEditProfile != null) btnEditProfile.setEnabled(!isLoading);
        if (btnSaveProfile != null) btnSaveProfile.setEnabled(!isLoading);
        if (btnCancelEdit != null) btnCancelEdit.setEnabled(!isLoading);
    }

    private void showError(String message) {
        if (isAdded() && getContext() != null) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Utility method to format phone number with +91 prefix
     */
    private String formatPhoneWithPrefix(String phone) {
        if (phone == null || phone.isEmpty()) {
            return "";
        }

        try {
            // Clean the phone number to digits only
            String digitsOnly = phone.replaceAll("[^0-9]", "");

            // Get last 10 digits or all if less than 10
            String last10Digits;
            if (digitsOnly.length() > 10) {
                last10Digits = digitsOnly.substring(digitsOnly.length() - 10);
            } else {
                last10Digits = digitsOnly;
            }

            return "+91" + last10Digits;
        } catch (Exception e) {
            Log.e(TAG, "Error formatting phone number", e);
            // Return original with prefix as fallback
            return "+91" + phone;
        }
    }

    /**
     * Utility method to extract 10 digits from phone number
     */
    private String extractTenDigits(String phone) {
        if (phone == null || phone.isEmpty()) {
            return "";
        }

        try {
            String noSpaces = phone.replace(" ", "");

            // Extract only digits
            String digitsOnly = noSpaces.replaceAll("[^0-9]", "");

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
            Log.e(TAG, "Error extracting phone digits", e);
            return phone; // Return original as fallback
        }
    }
}