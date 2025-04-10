package com.rescuereach.citizen;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.rescuereach.R;
import com.rescuereach.data.model.User;
import com.rescuereach.data.repository.OnCompleteListener;
import com.rescuereach.data.repository.UserRepository;
import com.rescuereach.data.repository.RepositoryProvider;
import com.rescuereach.service.auth.UserSessionManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

public class ProfileCompletionActivity extends AppCompatActivity {
    private static final String TAG = "ProfileCompletionActivity";
    private static final int PICK_CONTACT_REQUEST = 1;
    private static final int REQUEST_READ_CONTACTS = 2;

    // UI Components
    private TextInputEditText fullNameEditText;
    private TextInputEditText dobEditText;
    private AutoCompleteTextView genderDropdown;
    private AutoCompleteTextView stateDropdown;
    private TextInputEditText emergencyContactEditText;
    private TextInputLayout emergencyContactLayout;
    private RadioGroup volunteerRadioGroup;
    private RadioButton volunteerYesRadio;
    private RadioButton volunteerNoRadio;
    private Button saveButton;
    private ProgressBar progressBar;

    // Date formatting
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private Date selectedDob;

    // Service
    private UserSessionManager sessionManager;
    private UserRepository userRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_completion);

        // Initialize services
        sessionManager = UserSessionManager.getInstance(this);
        userRepository = RepositoryProvider.getUserRepository(this);

        // Check if user is authenticated
        if (sessionManager.getSavedPhoneNumber() == null) {
            // User not authenticated, redirect to authentication activity
            Toast.makeText(this, "Please authenticate first", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, PhoneAuthActivity.class));
            finish();
            return;
        }

        // Initialize views
        initializeViews();
        setupDropdowns();
        setupDatePicker();
        setupContactPicker();

        // Set up button click listener
        saveButton.setOnClickListener(v -> {
            // Validate and update profile
            if (validateInput()) {
                saveProfile();
            }
        });
    }

    private void initializeViews() {
        fullNameEditText = findViewById(R.id.edit_full_name);
        dobEditText = findViewById(R.id.edit_dob);
        genderDropdown = findViewById(R.id.dropdown_gender);
        stateDropdown = findViewById(R.id.dropdown_state);
        emergencyContactEditText = findViewById(R.id.edit_emergency_contact);
        emergencyContactLayout = findViewById(R.id.emergency_contact_layout);
        volunteerRadioGroup = findViewById(R.id.radio_volunteer);
        volunteerYesRadio = findViewById(R.id.radio_volunteer_yes);
        volunteerNoRadio = findViewById(R.id.radio_volunteer_no);
        saveButton = findViewById(R.id.button_save);
        progressBar = findViewById(R.id.progress_bar);

        // Default selection for volunteer radio
        volunteerNoRadio.setChecked(true);
    }

    private void setupDropdowns() {
        // Set up Gender dropdown
        String[] genderOptions = getResources().getStringArray(R.array.gender_options);
        ArrayAdapter<String> genderAdapter = new ArrayAdapter<>(
                this, R.layout.dropdown_item, genderOptions);
        genderDropdown.setAdapter(genderAdapter);

        // Set up State dropdown
        String[] stateOptions = getResources().getStringArray(R.array.indian_states);
        ArrayAdapter<String> stateAdapter = new ArrayAdapter<>(
                this, R.layout.dropdown_item, stateOptions);
        stateDropdown.setAdapter(stateAdapter);
    }

    private void setupDatePicker() {
        dobEditText.setOnClickListener(v -> showDatePickerDialog());
        // Prevent manual editing of the date field
        dobEditText.setFocusable(false);
        dobEditText.setClickable(true);
    }

    private void showDatePickerDialog() {
        // Get Current Date
        final Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR) - 18; // Default to 18 years ago
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        // If a date is already selected, use that
        if (selectedDob != null) {
            calendar.setTime(selectedDob);
            year = calendar.get(Calendar.YEAR);
            month = calendar.get(Calendar.MONTH);
            day = calendar.get(Calendar.DAY_OF_MONTH);
        }

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, year1, monthOfYear, dayOfMonth) -> {
                    Calendar selectedCalendar = Calendar.getInstance();
                    selectedCalendar.set(year1, monthOfYear, dayOfMonth);
                    selectedDob = selectedCalendar.getTime();
                    dobEditText.setText(dateFormat.format(selectedDob));
                    // Clear any previous error
                    dobEditText.setError(null);
                }, year, month, day);

        // Set maximum date to today - 10 years (minimum age)
        Calendar minAgeCalendar = Calendar.getInstance();
        minAgeCalendar.add(Calendar.YEAR, -10);
        datePickerDialog.getDatePicker().setMaxDate(minAgeCalendar.getTimeInMillis());

        // Set minimum date to 100 years ago (reasonable maximum age)
        Calendar maxAgeCalendar = Calendar.getInstance();
        maxAgeCalendar.add(Calendar.YEAR, -100);
        datePickerDialog.getDatePicker().setMinDate(maxAgeCalendar.getTimeInMillis());

        datePickerDialog.show();
    }

    private void setupContactPicker() {
        emergencyContactLayout.setEndIconOnClickListener(v -> {
            // Check for contacts permission
            if (ContextCompat.checkSelfPermission(ProfileCompletionActivity.this,
                    android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(ProfileCompletionActivity.this,
                        new String[]{android.Manifest.permission.READ_CONTACTS}, REQUEST_READ_CONTACTS);
            } else {
                pickContact();
            }
        });
    }

    private void pickContact() {
        Intent pickContactIntent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        pickContactIntent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
        startActivityForResult(pickContactIntent, PICK_CONTACT_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_READ_CONTACTS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickContact();
            } else {
                Toast.makeText(this, "Contact permission is needed to select contacts", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_CONTACT_REQUEST && resultCode == RESULT_OK) {
            if (data != null) {
                Uri contactUri = data.getData();
                if (contactUri != null) {
                    String[] projection = new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER};

                    try (Cursor cursor = getContentResolver().query(contactUri, projection, null, null, null)) {
                        if (cursor != null && cursor.moveToFirst()) {
                            int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                            String number = cursor.getString(numberIndex);

                            // Format and clean the phone number
                            number = cleanPhoneNumber(number);
                            emergencyContactEditText.setText(number);
                            // Clear any previous error
                            emergencyContactEditText.setError(null);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error fetching contact", e);
                        Toast.makeText(this, "Error fetching contact", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    private String cleanPhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return "";

        // Remove any non-digit characters
        String cleaned = phoneNumber.replaceAll("[^\\d]", "");

        // If it has a country code, remove it
        if (cleaned.startsWith("91") && cleaned.length() > 10) {
            cleaned = cleaned.substring(2);
        }

        // Ensure it's just 10 digits
        if (cleaned.length() > 10) {
            cleaned = cleaned.substring(cleaned.length() - 10);
        }

        return cleaned;
    }

    private boolean validateInput() {
        boolean isValid = true;

        // Validate full name
        String fullName = fullNameEditText.getText().toString().trim();
        if (TextUtils.isEmpty(fullName)) {
            fullNameEditText.setError("Full name is required");
            fullNameEditText.requestFocus();
            isValid = false;
        } else if (fullName.length() < 3) {
            fullNameEditText.setError("Full name must be at least 3 characters");
            fullNameEditText.requestFocus();
            isValid = false;
        } else if (!Pattern.matches("^[\\p{L} .'-]+$", fullName)) {
            // Allow letters, spaces, periods, apostrophes, and hyphens
            fullNameEditText.setError("Name contains invalid characters");
            fullNameEditText.requestFocus();
            isValid = false;
        }

        // Validate date of birth
        String dob = dobEditText.getText().toString().trim();
        if (TextUtils.isEmpty(dob)) {
            dobEditText.setError("Date of birth is required");
            if (isValid) dobEditText.requestFocus();
            isValid = false;
        }

        // Validate gender
        String gender = genderDropdown.getText().toString().trim();
        if (TextUtils.isEmpty(gender)) {
            genderDropdown.setError("Gender is required");
            if (isValid) genderDropdown.requestFocus();
            isValid = false;
        }

        // Validate state
        String state = stateDropdown.getText().toString().trim();
        if (TextUtils.isEmpty(state)) {
            stateDropdown.setError("State is required");
            if (isValid) stateDropdown.requestFocus();
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
        } else {
            // Check if emergency contact is the same as user's phone number
            String userPhone = sessionManager.getSavedPhoneNumber();
            if (userPhone != null) {
                // Clean both numbers for comparison
                String cleanedUserPhone = cleanPhoneNumber(userPhone);
                if (cleanedUserPhone.equals(emergencyContact)) {
                    emergencyContactEditText.setError("Emergency contact cannot be your own number");
                    if (isValid) emergencyContactEditText.requestFocus();
                    isValid = false;
                }
            }
        }

        return isValid;
    }

    private boolean isValidPhoneNumber(String phoneNumber) {
        if (TextUtils.isEmpty(phoneNumber)) {
            return false;
        }

        // Remove any non-digit characters
        String cleaned = phoneNumber.replaceAll("[^\\d]", "");

        // Should be exactly 10 digits and start with 6, 7, 8, or 9
        return cleaned.length() == 10 && Pattern.matches("^[6-9]\\d{9}$", cleaned);
    }

    /**
     * Update the volunteer status in both SharedPreferences and Firebase
     * This ensures the volunteer status persists correctly between sessions
     *
     * @param isVolunteer Whether the user wants to be a volunteer
     */
    private void updateVolunteerStatus(boolean isVolunteer) {
        Log.d(TAG, "Updating volunteer status to: " + isVolunteer);

        // First update in SharedPreferences
        sessionManager.getSharedPreferences().edit()
                .putBoolean("is_volunteer", isVolunteer)
                .apply();

        // Create a User object for Firebase update
        User user = new User();
        user.setUserId(sessionManager.getUserId());
        user.setPhoneNumber(sessionManager.getSavedPhoneNumber());
        user.setVolunteer(isVolunteer);

        // Update in Firebase to ensure persistence
        userRepository.updateUserProfile(user, new OnCompleteListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Volunteer status updated in Firebase: " + isVolunteer);
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Error updating volunteer status in Firebase", e);
            }
        });
    }

    private void saveProfile() {
        // Get values from UI
        String fullName = fullNameEditText.getText().toString().trim();
        String emergencyContact = emergencyContactEditText.getText().toString().trim();
        String gender = genderDropdown.getText().toString().trim();
        String state = stateDropdown.getText().toString().trim();
        boolean isVolunteer = volunteerYesRadio.isChecked();

        // Validate again just to be sure
        if (!validateInput()) {
            return;
        }

        showLoading(true);

        // Format emergency contact to include country code if not present
        if (!emergencyContact.startsWith("+")) {
            emergencyContact = "+91" + emergencyContact;
        }

        // Update volunteer status directly in Firebase to ensure persistence
        updateVolunteerStatus(isVolunteer);

        // Save profile with the correct parameter order
        // Per UserSessionManager implementation: fullName, emergencyContact, dateOfBirth, gender, state, isVolunteer
        sessionManager.updateUserProfile(
                fullName,
                emergencyContact,
                selectedDob,
                gender,
                state,
                isVolunteer,
                new OnCompleteListener() {
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

                        // Show a detailed error dialog with retry option
                        showErrorDialog("Profile Update Failed",
                                "We couldn't save your profile. " +
                                        (e.getMessage() != null ? e.getMessage() : "Please try again."));
                    }
                }
        );
    }

    private void showErrorDialog(String title, String message) {
        if (isFinishing() || isDestroyed()) return;

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Retry", (dialog, which) -> {
                    dialog.dismiss();
                    saveProfile();
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    private void showLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        saveButton.setEnabled(!isLoading);
        fullNameEditText.setEnabled(!isLoading);
        dobEditText.setEnabled(!isLoading);
        genderDropdown.setEnabled(!isLoading);
        stateDropdown.setEnabled(!isLoading);
        emergencyContactEditText.setEnabled(!isLoading);
        volunteerYesRadio.setEnabled(!isLoading);
        volunteerNoRadio.setEnabled(!isLoading);
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, CitizenMainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}