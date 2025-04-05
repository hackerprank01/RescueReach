package com.rescuereach.service.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.rescuereach.data.model.User;
import com.rescuereach.data.repository.OnCompleteListener;
import com.rescuereach.data.repository.RepositoryProvider;
import com.rescuereach.data.repository.UserRepository;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class UserSessionManager {
    private static final String TAG = "UserSessionManager";
    private static final String PREF_NAME = "RescueReachUserSession";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USER_PHONE = "userPhone";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_USER_FULL_NAME = "user_full_name";
    private static final String KEY_USER_EMERGENCY_CONTACT = "user_emergency_contact";
    private static final String KEY_IS_PROFILE_COMPLETE = "is_profile_complete";

    // New preference keys
    private static final String KEY_USER_DOB = "user_dob";
    private static final String KEY_USER_GENDER = "user_gender";
    private static final String KEY_USER_STATE = "user_state";
    private static final String KEY_USER_IS_VOLUNTEER = "user_is_volunteer";


    private static final String KEY_EMERGENCY_CONTACT_PHONE = "emergency_contact_phone";

    private static final String PREF_NOTIFICATION = "notification_";
    private static final String PREF_PRIVACY = "privacy_";
    private static final String PREF_APPEARANCE = "appearance_";
    private static final String PREF_EMERGENCY = "emergency_";
    private static final String PREF_DATA = "data_";

    // For backward compatibility
    private static final String KEY_USER_FIRST_NAME = "user_first_name";
    private static final String KEY_USER_LAST_NAME = "user_last_name";
    private final AuthService authService;
    private final UserRepository userRepository;
    private final SimpleDateFormat dateFormat;
    private static UserSessionManager instance;
    private final SharedPreferences sharedPreferences;
    private final Context context;


    private User currentUser;

    private UserSessionManager(Context context) {
        this.context = context.getApplicationContext();
        this.sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.authService = AuthServiceProvider.getInstance().getAuthService();
        this.userRepository = RepositoryProvider.getInstance().getUserRepository();
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    }

    public static synchronized UserSessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new UserSessionManager(context);
        }
        return instance;
    }

    public void saveUserPhoneNumber(String phoneNumber) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_USER_PHONE, formatPhoneNumber(phoneNumber));
        editor.apply();
    }

    public String getSavedPhoneNumber() {
        return sharedPreferences.getString(KEY_USER_PHONE, null);
    }

    // Update to save full profile data with new fields
    public void saveUserProfileData(String fullName, String emergencyContact,
                                    Date dateOfBirth, String gender, String state, boolean isVolunteer) {
        SharedPreferences.Editor editor = sharedPreferences.edit();

        // Save new format data
        editor.putString(KEY_USER_FULL_NAME, fullName);
        editor.putString(KEY_USER_EMERGENCY_CONTACT, formatPhoneNumber(emergencyContact));

        // Save new fields
        if (dateOfBirth != null) {
            editor.putString(KEY_USER_DOB, dateFormat.format(dateOfBirth));
        }
        editor.putString(KEY_USER_GENDER, gender);
        editor.putString(KEY_USER_STATE, state);
        editor.putBoolean(KEY_USER_IS_VOLUNTEER, isVolunteer);

        // For backward compatibility - extract first and last name from full name
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
        editor.putString(KEY_USER_FIRST_NAME, firstName);
        editor.putString(KEY_USER_LAST_NAME, lastName);

        editor.putBoolean(KEY_IS_PROFILE_COMPLETE, true);
        editor.apply();
    }

    // For backward compatibility
    public void saveUserProfileData(String firstName, String lastName, String emergencyContact) {
        String fullName;
        if (firstName != null && lastName != null && !firstName.isEmpty() && !lastName.isEmpty()) {
            fullName = firstName + " " + lastName;
        } else if (firstName != null && !firstName.isEmpty()) {
            fullName = firstName;
        } else if (lastName != null && !lastName.isEmpty()) {
            fullName = lastName;
        } else {
            fullName = "";
        }

        // Use default values for new fields
        saveUserProfileData(fullName, emergencyContact, null, "", "", false);
    }
    public boolean getNotificationPreference(String key, boolean defaultValue) {
        return sharedPreferences.getBoolean(PREF_NOTIFICATION + key, defaultValue);
    }
    public void setNotificationPreference(String key, boolean value) {
        sharedPreferences.edit().putBoolean(PREF_NOTIFICATION + key, value).apply();
    }
    public boolean getPrivacyPreference(String key, boolean defaultValue) {
        return sharedPreferences.getBoolean(PREF_PRIVACY + key, defaultValue);
    }
    public void setPrivacyPreference(String key, boolean value) {
        sharedPreferences.edit().putBoolean(PREF_PRIVACY + key, value).apply();
    }
    public String getAppearancePreference(String key, String defaultValue) {
        return sharedPreferences.getString(PREF_APPEARANCE + key, defaultValue);
    }
    public void setAppearancePreference(String key, String value) {
        sharedPreferences.edit().putString(PREF_APPEARANCE + key, value).apply();
    }
    public boolean getEmergencyPreference(String key, boolean defaultValue) {
        return sharedPreferences.getBoolean(PREF_EMERGENCY + key, defaultValue);
    }
    public void setEmergencyPreference(String key, boolean value) {
        sharedPreferences.edit().putBoolean(PREF_EMERGENCY + key, value).apply();
    }
    public boolean getDataPreference(String key, boolean defaultValue) {
        return sharedPreferences.getBoolean(PREF_DATA + key, defaultValue);
    }
    public void setDataPreference(String key, boolean value) {
        sharedPreferences.edit().putBoolean(PREF_DATA + key, value).apply();
    }
    public int getIntPreference(String key, int defaultValue) {
        return sharedPreferences.getInt(key, defaultValue);
    }
    public void setIntPreference(String key, int value) {
        sharedPreferences.edit().putInt(key, value).apply();
    }
    public long getLongPreference(String key, long defaultValue) {
        return sharedPreferences.getLong(key, defaultValue);
    }
    public void setLongPreference(String key, long value) {
        sharedPreferences.edit().putLong(key, value).apply();
    }
    public float getFloatPreference(String key, float defaultValue) {
        return sharedPreferences.getFloat(key, defaultValue);
    }
    public void setFloatPreference(String key, float value) {
        sharedPreferences.edit().putFloat(key, value).apply();
    }
    public String getStringPreference(String key, String defaultValue) {
        return sharedPreferences.getString(key, defaultValue);
    }
    public void setStringPreference(String key, String value) {
        sharedPreferences.edit().putString(key, value).apply();
    }

    public boolean isProfileComplete() {
        return sharedPreferences.getBoolean(KEY_IS_PROFILE_COMPLETE, false);
    }

    public void clearLocalData() {
        SharedPreferences.Editor editor = sharedPreferences.edit();

        // Keep login information
        String userId = getUserId();
        String phoneNumber = getSavedPhoneNumber();
        boolean isLoggedIn = isLoggedIn();

        // Clear everything
        editor.clear();

        // Restore login information
        if (userId != null) {
            editor.putString(KEY_USER_ID, userId);
        }
        if (phoneNumber != null) {
            editor.putString(KEY_USER_PHONE, phoneNumber);
        }
        editor.putBoolean(KEY_IS_LOGGED_IN, isLoggedIn);

        editor.apply();
    }

    public boolean isLoggedIn() {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    /**
     * Get all preferences as a map
     * @return Map of all preferences
     */
    public Map<String, Object> getAllPreferences() {
        Map<String, Object> allPreferences = new HashMap<>();

        // Get all preferences
        Map<String, ?> all = sharedPreferences.getAll();

        // Skip login/auth related keys
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            String key = entry.getKey();
            if (!key.equals(KEY_USER_ID) && !key.equals(KEY_USER_PHONE) &&
                    !key.equals(KEY_IS_LOGGED_IN)) {
                allPreferences.put(key, entry.getValue());
            }
        }

        return allPreferences;
    }

    public void restorePreferences(Map<String, Object> preferences) {
        SharedPreferences.Editor editor = sharedPreferences.edit();

        // Keep login information
        String userId = getUserId();
        String phoneNumber = getSavedPhoneNumber();
        boolean isLoggedIn = isLoggedIn();

        // Restore all preferences
        for (Map.Entry<String, Object> entry : preferences.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // Skip login/auth related keys
            if (key.equals(KEY_USER_ID) || key.equals(KEY_USER_PHONE) ||
                    key.equals(KEY_IS_LOGGED_IN)) {
                continue;
            }

            if (value instanceof String) {
                editor.putString(key, (String) value);
            } else if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);
            } else if (value instanceof Integer) {
                editor.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                editor.putLong(key, (Long) value);
            } else if (value instanceof Float) {
                editor.putFloat(key, (Float) value);
            }
        }

        // Restore login information
        if (userId != null) {
            editor.putString(KEY_USER_ID, userId);
        }
        if (phoneNumber != null) {
            editor.putString(KEY_USER_PHONE, phoneNumber);
        }
        editor.putBoolean(KEY_IS_LOGGED_IN, isLoggedIn);

        editor.apply();
    }

    // Get user full name
    public String getFullName() {
        String fullName = sharedPreferences.getString(KEY_USER_FULL_NAME, null);
        if (fullName != null && !fullName.isEmpty()) {
            return fullName;
        }

        // Fallback to first name + last name for backward compatibility
        String firstName = sharedPreferences.getString(KEY_USER_FIRST_NAME, "");
        String lastName = sharedPreferences.getString(KEY_USER_LAST_NAME, "");

        if (!firstName.isEmpty() && !lastName.isEmpty()) {
            return firstName + " " + lastName;
        } else if (!firstName.isEmpty()) {
            return firstName;
        } else if (!lastName.isEmpty()) {
            return lastName;
        } else {
            return "User";
        }
    }

    // For backward compatibility
    public String getFirstName() {
        String fullName = sharedPreferences.getString(KEY_USER_FULL_NAME, null);
        if (fullName != null && !fullName.isEmpty()) {
            int spaceIndex = fullName.indexOf(' ');
            if (spaceIndex > 0) {
                return fullName.substring(0, spaceIndex);
            }
            return fullName;
        }
        return sharedPreferences.getString(KEY_USER_FIRST_NAME, "");
    }

    // For backward compatibility
    public String getLastName() {
        String fullName = sharedPreferences.getString(KEY_USER_FULL_NAME, null);
        if (fullName != null && !fullName.isEmpty()) {
            int spaceIndex = fullName.indexOf(' ');
            if (spaceIndex > 0 && spaceIndex < fullName.length() - 1) {
                return fullName.substring(spaceIndex + 1);
            }
            return "";
        }
        return sharedPreferences.getString(KEY_USER_LAST_NAME, "");
    }

    public String getEmergencyContact() {
        return sharedPreferences.getString(KEY_USER_EMERGENCY_CONTACT, "");
    }

    // New getters for additional fields
    public Date getDateOfBirth() {
        String dobString = sharedPreferences.getString(KEY_USER_DOB, null);
        if (dobString != null && !dobString.isEmpty()) {
            try {
                return dateFormat.parse(dobString);
            } catch (ParseException e) {
                Log.e(TAG, "Error parsing date of birth", e);
            }
        }
        return null;
    }

    public String getGender() {
        return sharedPreferences.getString(KEY_USER_GENDER, "");
    }

    public String getState() {
        return sharedPreferences.getString(KEY_USER_STATE, "");
    }

    public boolean isVolunteer() {
        return sharedPreferences.getBoolean(KEY_USER_IS_VOLUNTEER, false);
    }

    public void createNewUser(String phoneNumber, OnCompleteListener listener) {
        String userId = authService.getCurrentUserId();
        if (userId == null) {
            listener.onError(new Exception("User ID is null"));
            return;
        }

        User newUser = new User(userId, formatPhoneNumber(phoneNumber));
        userRepository.saveUser(newUser, new OnCompleteListener() {
            @Override
            public void onSuccess() {
                currentUser = newUser;
                saveUserPhoneNumber(phoneNumber);
                listener.onSuccess();
            }

            @Override
            public void onError(Exception e) {
                listener.onError(e);
            }
        });
    }

    // Update method for the new profile data model
    public void updateUserProfile(String fullName, String emergencyContact,
                                  Date dateOfBirth, String gender, String state, boolean isVolunteer,
                                  OnCompleteListener listener) {
        String userId = authService.getCurrentUserId();
        String phoneNumber = getSavedPhoneNumber();

        if (userId == null || phoneNumber == null) {
            listener.onError(new Exception("User ID or phone number is missing"));
            return;
        }

        User updatedUser = new User(
                userId,
                fullName,
                phoneNumber,
                formatPhoneNumber(emergencyContact),
                dateOfBirth,
                gender,
                state,
                isVolunteer
        );

        userRepository.updateUserProfile(updatedUser, new OnCompleteListener() {
            @Override
            public void onSuccess() {
                currentUser = updatedUser;
                saveUserProfileData(fullName, emergencyContact, dateOfBirth, gender, state, isVolunteer);
                listener.onSuccess();
            }

            @Override
            public void onError(Exception e) {
                listener.onError(e);
            }
        });
    }

    // For backward compatibility
    public void updateUserProfile(String firstName, String lastName, String emergencyContact, OnCompleteListener listener) {
        String fullName;
        if (firstName != null && lastName != null && !firstName.isEmpty() && !lastName.isEmpty()) {
            fullName = firstName + " " + lastName;
        } else if (firstName != null && !firstName.isEmpty()) {
            fullName = firstName;
        } else if (lastName != null && !lastName.isEmpty()) {
            fullName = lastName;
        } else {
            fullName = "";
        }

        // Use default values for new fields
        updateUserProfile(fullName, emergencyContact, null, "", "", false, listener);
    }

    public void markProfileComplete() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_IS_PROFILE_COMPLETE, true);
        editor.apply();
    }

    public void resetProfileCompletionStatus() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_IS_PROFILE_COMPLETE, false);
        editor.apply();
    }

    public void loadCurrentUser(final OnUserLoadedListener listener) {
        String phoneNumber = getSavedPhoneNumber();
        if (phoneNumber == null) {
            listener.onError(new Exception("No saved phone number"));
            return;
        }

        userRepository.getUserByPhoneNumber(phoneNumber, new UserRepository.OnUserFetchedListener() {
            @Override
            public void onSuccess(User user) {
                currentUser = user;
                listener.onUserLoaded(user);
            }

            @Override
            public void onError(Exception e) {
                listener.onError(e);
            }
        });
    }

    private String formatPhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return null;

        // Remove any non-digit characters
        String cleaned = phoneNumber.replaceAll("[^\\d+]", "");

        // Ensure it starts with +91
        if (!cleaned.startsWith("+")) {
            cleaned = "+91" + cleaned;
        } else if (!cleaned.startsWith("+91")) {
            cleaned = "+91" + cleaned.substring(1);
        }

        return cleaned;
    }

    public void clearSession() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
        currentUser = null;
    }

    // Interface for user loading callback
    public interface OnUserLoadedListener {
        void onUserLoaded(User user);
        void onError(Exception e);
    }

    //------------------------------------------------------------------------------
    // New methods for the Profile UI implementation
    //------------------------------------------------------------------------------

    // Set the full name directly
    public void setFullName(String fullName) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_USER_FULL_NAME, fullName);

        // For backward compatibility - extract first and last name
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
        editor.putString(KEY_USER_FIRST_NAME, firstName);
        editor.putString(KEY_USER_LAST_NAME, lastName);
        editor.apply();
    }

    // Set and get date of birth as string (for UI)
    public void setDateOfBirth(String dobString) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_USER_DOB, dobString);
        editor.apply();
    }

    public String getDateOfBirthString() {
        return sharedPreferences.getString(KEY_USER_DOB, "");
    }

    // Set gender directly
    public void setGender(String gender) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_USER_GENDER, gender);
        editor.apply();
    }

    // Set state directly
    public void setState(String state) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_USER_STATE, state);
        editor.apply();
    }

    // Set volunteer status directly
    public void setVolunteer(boolean isVolunteer) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_USER_IS_VOLUNTEER, isVolunteer);
        editor.apply();
    }

    // Emergency contact methods




    public void setEmergencyContactPhone(String phone) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_EMERGENCY_CONTACT_PHONE, formatPhoneNumber(phone));
        // For backward compatibility
        editor.putString(KEY_USER_EMERGENCY_CONTACT, formatPhoneNumber(phone));
        editor.apply();
    }

    public String getUserId() {
        return authService.getCurrentUserId();
    }
    public String getEmergencyContactPhone() {
        String phone = sharedPreferences.getString(KEY_EMERGENCY_CONTACT_PHONE, "");
        if (phone == null || phone.isEmpty()) {
            // Fall back to the legacy key
            return sharedPreferences.getString(KEY_USER_EMERGENCY_CONTACT, "");
        }
        return phone;
    }



}