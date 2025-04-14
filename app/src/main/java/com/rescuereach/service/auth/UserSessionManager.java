package com.rescuereach.service.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;
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
import java.util.TimeZone;

/**
 * Manages user session data using SharedPreferences and synchronizes with Firebase.
 * Handles login state, user profile information, and preferences.
 */
public class UserSessionManager {
    private static final String TAG = "UserSessionManager";

    private boolean volunteerStatusLoading = false;
    private long lastVolunteerCheckTime = 0;
    private static final long VOLUNTEER_CHECK_INTERVAL = 60000;

    // SharedPreferences keys
    private static final String PREF_NAME = "RescueReachSession";
    private static final String KEY_PHONE_NUMBER = "phone_number";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_FULL_NAME = "full_name";
    private static final String KEY_FIRST_NAME = "first_name";
    private static final String KEY_LAST_NAME = "last_name";
    private static final String KEY_GENDER = "gender";
    private static final String KEY_DATE_OF_BIRTH = "date_of_birth";
    private static final String KEY_STATE = "state";
    private static final String KEY_IS_VOLUNTEER = "is_volunteer";
    private static final String KEY_EMERGENCY_CONTACT = "emergency_contact";
    private static final String KEY_LAST_LOGIN = "last_login";
    private static final String KEY_PROFILE_COMPLETED = "profile_completed";

    // Preference categories
    private static final String PRIVACY_PREFS = "privacy_settings";
    private static final String EMERGENCY_PREFS = "emergency_settings";
    private static final String NOTIFICATION_PREFS = "notification_settings";

    private static UserSessionManager instance;
    private final SharedPreferences sharedPreferences;
    private final SharedPreferences privacyPreferences;
    private final SharedPreferences emergencyPreferences;
    private final SharedPreferences notificationPreferences;
    private final Context context;

    // Date formatters for storing and retrieving dates
    private final SimpleDateFormat storageFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final SimpleDateFormat displayFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
    private final SimpleDateFormat isoFormat;

    // Firebase references
    private final FirebaseAuth firebaseAuth;
    private final FirebaseFirestore firestore;
    private final DatabaseReference realtimeDb;
    private UserRepository userRepository;

    private UserSessionManager(Context context) {
        this.context = context.getApplicationContext();

        // Initialize SharedPreferences
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        privacyPreferences = context.getSharedPreferences(PRIVACY_PREFS, Context.MODE_PRIVATE);
        emergencyPreferences = context.getSharedPreferences(EMERGENCY_PREFS, Context.MODE_PRIVATE);
        notificationPreferences = context.getSharedPreferences(NOTIFICATION_PREFS, Context.MODE_PRIVATE);

        // Initialize date formatters
        isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        // Initialize Firebase
        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        realtimeDb = FirebaseDatabase.getInstance().getReference();

        // Get user repository
        userRepository = RepositoryProvider.getUserRepository(context);
    }

    public SharedPreferences getSharedPreferences() {
        return sharedPreferences;
    }

    /**
     * Get the singleton instance of UserSessionManager
     * @param context Application context
     * @return UserSessionManager instance
     */
    public static synchronized UserSessionManager getInstance(Context context) {
        if (instance == null) {
            // CRITICAL FIX: Check if context is null before creating instance
            if (context == null) {
                Log.e(TAG, "Context is null in getInstance(), cannot create UserSessionManager");
                return null;
            }
            instance = new UserSessionManager(context);
        }
        return instance;
    }

    /**
     * Save the user's phone number after successful authentication
     * @param phoneNumber The user's phone number
     */
    public void saveUserPhoneNumber(String phoneNumber) {
        // Format the phone number
        String formattedPhone = formatPhoneNumber(phoneNumber);

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_PHONE_NUMBER, formattedPhone);
        editor.putLong(KEY_LAST_LOGIN, System.currentTimeMillis());

        // Set user ID from Firebase Auth
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user != null) {
            editor.putString(KEY_USER_ID, user.getUid());
        }

        editor.apply();

        // Update user's online status in Realtime Database
        updateOnlineStatus();

        // This will ensure we have the correct volunteer status
        loadUserProfileFromFirebase(formattedPhone);
    }


    private void loadUserProfileFromFirebase(String phoneNumber) {
        if (userRepository == null) {
            Log.e(TAG, "UserRepository is null");
            return;
        }

        Log.d(TAG, "Loading user profile from Firebase for: " + phoneNumber);

        // First check if the user exists, and if so, load their profile
        try {
            userRepository.getUserByPhoneNumber(phoneNumber, new UserRepository.OnUserFetchedListener() {
                @Override
                public void onSuccess(User user) {
                    if (user != null) {
                        Log.d(TAG, "User found in Firebase, updating local data");
                        // Update SharedPreferences with user data from Firebase
                        SharedPreferences.Editor editor = sharedPreferences.edit();

                        if (user.getFullName() != null) {
                            editor.putString(KEY_FULL_NAME, user.getFullName());
                            editor.putString(KEY_FIRST_NAME, user.getFirstName());
                            editor.putString(KEY_LAST_NAME, user.getLastName());
                        }

                        if (user.getGender() != null) {
                            editor.putString(KEY_GENDER, user.getGender());
                        }

                        if (user.getState() != null) {
                            editor.putString(KEY_STATE, user.getState());
                        }

                        if (user.getEmergencyContact() != null) {
                            editor.putString(KEY_EMERGENCY_CONTACT, user.getEmergencyContact());
                        }

                        // CRITICAL: This is the key fix - always update volunteer status from Firebase
                        editor.putBoolean(KEY_IS_VOLUNTEER, user.isVolunteer());
                        Log.d(TAG, "Setting volunteer status from Firebase: " + user.isVolunteer());

                        if (user.getDateOfBirth() != null) {
                            editor.putString(KEY_DATE_OF_BIRTH, storageFormat.format(user.getDateOfBirth()));
                        }

                        // CRITICAL FIX: Always set profile completion status based on required fields
                        boolean isComplete = !TextUtils.isEmpty(user.getFullName()) &&
                                !TextUtils.isEmpty(user.getEmergencyContact());
                        editor.putBoolean(KEY_PROFILE_COMPLETED, isComplete);
                        Log.d(TAG, "Setting profile completion status: " + isComplete);

                        editor.apply();

                        // Now we can update online status
                        updateOnlineStatus();
                    } else {
                        Log.d(TAG, "User not found in Firebase");
                    }
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Error loading user profile from Firebase", e);
                    // Still update online status on error
                    updateOnlineStatus();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Exception in loadUserProfileFromFirebase", e);
            updateOnlineStatus();
        }
    }


    /**
     * Create a new user in Firebase after phone authentication
     * @param phoneNumber User's phone number
     * @param listener Callback for operation result
     */
    public void createNewUser(String phoneNumber, OnCompleteListener listener) {
        try {
            // Save phone number to session (formats the number)
            saveUserPhoneNumber(phoneNumber);

            // Create user object
            User user = new User();
            user.setPhoneNumber(formatPhoneNumber(phoneNumber));
            user.setCreatedAt(new Date());

            // Get Firebase Auth user ID
            FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
            if (firebaseUser != null) {
                user.setUserId(firebaseUser.getUid());
            }

            user.setVolunteer(false);

            // Save to Firebase
            userRepository.saveUser(user, new OnCompleteListener() {
                @Override
                public void onSuccess() {
                    if (listener != null) {
                        listener.onSuccess();
                    }
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Error creating new user", e);
                    if (listener != null) {
                        listener.onError(e);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in createNewUser", e);
            if (listener != null) {
                listener.onError(e);
            }
        }
    }

    public void checkVolunteerStatus(VolunteerStatusCallback callback) {
        // Get phone number
        String phoneNumber = getSavedPhoneNumber();
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            if (callback != null) {
                callback.onResult(false);
            }
            return;
        }

        // Check if we already have a recent check
        long now = System.currentTimeMillis();
        boolean cachedStatus = sharedPreferences.getBoolean(KEY_IS_VOLUNTEER, false);

        if (now - lastVolunteerCheckTime < VOLUNTEER_CHECK_INTERVAL) {
            // Use cached value if recent enough
            Log.d(TAG, "Using cached volunteer status: " + cachedStatus);
            if (callback != null) {
                callback.onResult(cachedStatus);
            }
            return;
        }

        // Don't do multiple simultaneous checks
        if (volunteerStatusLoading) {
            Log.d(TAG, "Volunteer status check already in progress, using cached: " + cachedStatus);
            if (callback != null) {
                callback.onResult(cachedStatus);
            }
            return;
        }

        volunteerStatusLoading = true;

        // Always do a direct Firebase check for critical status
        userRepository.getUserByPhoneNumber(phoneNumber, new UserRepository.OnUserFetchedListener() {
            @Override
            public void onSuccess(User user) {
                volunteerStatusLoading = false;
                lastVolunteerCheckTime = System.currentTimeMillis();

                boolean isVolunteer = false;
                if (user != null) {
                    isVolunteer = user.isVolunteer();
                    Log.d(TAG, "Firebase volunteer status check: " + isVolunteer);

                    // Update SharedPreferences
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putBoolean(KEY_IS_VOLUNTEER, isVolunteer);
                    editor.apply();
                }

                if (callback != null) {
                    callback.onResult(isVolunteer);
                }
            }

            @Override
            public void onError(Exception e) {
                volunteerStatusLoading = false;
                Log.e(TAG, "Error checking volunteer status", e);

                // Fall back to cached value on error
                boolean cachedValue = sharedPreferences.getBoolean(KEY_IS_VOLUNTEER, false);
                if (callback != null) {
                    callback.onResult(cachedValue);
                }
            }
        });
    }

    public interface VolunteerStatusCallback {
        void onResult(boolean isVolunteer);
    }

    /**
     * Update user profile with all fields and save to both SharedPreferences and Firebase
     */
    public void updateUserProfile(String fullName, String emergencyContact, Date dateOfBirth,
                                  String gender, String state, boolean isVolunteer,
                                  OnCompleteListener listener) {
        try {
            // Format the phone number and emergency contact
            String phoneNumber = formatPhoneNumber(getSavedPhoneNumber());
            emergencyContact = formatPhoneNumber(emergencyContact);

            // Save to SharedPreferences
            SharedPreferences.Editor editor = sharedPreferences.edit();

            // Parse full name
            String firstName = fullName;
            String lastName = "";
            int spaceIndex = fullName.indexOf(' ');
            if (spaceIndex > 0) {
                firstName = fullName.substring(0, spaceIndex);
                lastName = fullName.substring(spaceIndex + 1);
            }

            // Save all fields
            editor.putString(KEY_FULL_NAME, fullName);
            editor.putString(KEY_FIRST_NAME, firstName);
            editor.putString(KEY_LAST_NAME, lastName);
            editor.putString(KEY_GENDER, gender);
            editor.putString(KEY_STATE, state);
            editor.putString(KEY_EMERGENCY_CONTACT, emergencyContact);
            editor.putBoolean(KEY_IS_VOLUNTEER, isVolunteer);
            editor.putBoolean(KEY_PROFILE_COMPLETED, true);

            // Save date of birth if provided
            if (dateOfBirth != null) {
                editor.putString(KEY_DATE_OF_BIRTH, storageFormat.format(dateOfBirth));
            }

            editor.apply();

            // Create User object for Firebase update
            User user = new User();
            user.setUserId(getUserId());
            user.setPhoneNumber(phoneNumber);
            user.setFullName(fullName);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setGender(gender);
            user.setState(state);
            user.setEmergencyContact(emergencyContact);
            user.setVolunteer(isVolunteer);
            user.setDateOfBirth(dateOfBirth);

            // Update Firebase
            userRepository.updateUserProfile(user, new OnCompleteListener() {
                @Override
                public void onSuccess() {
                    if (listener != null) {
                        listener.onSuccess();
                    }
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Error updating user profile in Firebase", e);
                    if (listener != null) {
                        listener.onError(e);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in updateUserProfile", e);
            if (listener != null) {
                listener.onError(e);
            }
        }
    }

    /**
     * Update the user's online status in Realtime Database
     */
    private void updateOnlineStatus() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        String phoneNumber = getSavedPhoneNumber();

        if (user != null && phoneNumber != null) {
            // Format phone number for database key
            String phoneKey = phoneNumber.replaceAll("[^\\d]", "");

            // Update online status
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "online");
            updates.put("lastSeen", System.currentTimeMillis());

            // Try updating by phone key first
            realtimeDb.child("users").child(phoneKey).updateChildren(updates)
                    .addOnFailureListener(e -> {
                        // If that fails, try with UID
                        realtimeDb.child("users").child(user.getUid()).updateChildren(updates)
                                .addOnFailureListener(e2 ->
                                        Log.e(TAG, "Failed to update online status", e2));
                    });
        }
    }

    /**
     * Update the user's offline status on logout
     */
    private void updateOfflineStatus() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        String phoneNumber = getSavedPhoneNumber();

        if (user != null && phoneNumber != null) {
            // Format phone number for database key
            String phoneKey = phoneNumber.replaceAll("[^\\d]", "");

            // Update offline status
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "offline");
            updates.put("lastSeen", System.currentTimeMillis());

            // Try updating by phone key first
            realtimeDb.child("users").child(phoneKey).updateChildren(updates);
            // Also try with UID to be sure
            realtimeDb.child("users").child(user.getUid()).updateChildren(updates);
        }
    }

    /**
     * Get the stored phone number
     * @return The user's phone number or null if not set
     */
    public String getSavedPhoneNumber() {
        return sharedPreferences.getString(KEY_PHONE_NUMBER, null);
    }

    /**
     * Get the user ID (Firebase Auth UID)
     * @return The user ID or null if not set
     */
    public String getUserId() {
        // First try to get from SharedPreferences
        String userId = sharedPreferences.getString(KEY_USER_ID, null);

        // If not available, try to get from Firebase Auth
        if (userId == null) {
            FirebaseUser user = firebaseAuth.getCurrentUser();
            if (user != null) {
                userId = user.getUid();

                // Save it for future use
                sharedPreferences.edit().putString(KEY_USER_ID, userId).apply();
            }
        }

        return userId;
    }

    /**
     * Check if the user is logged in
     * @return true if logged in, false otherwise
     */
    public boolean isLoggedIn() {
        // Check both local storage and Firebase Auth
        boolean localLogin = getSavedPhoneNumber() != null;
        boolean firebaseLogin = firebaseAuth.getCurrentUser() != null;

        return localLogin && firebaseLogin;
    }

    /**
     * Check if the user's profile is complete with essential information
     * @return true if profile is complete, false otherwise
     */
    public boolean isProfileComplete() {
        // First check if we have explicitly marked the profile as complete
        if (sharedPreferences.getBoolean(KEY_PROFILE_COMPLETED, false)) {
            return true;
        }

        // Otherwise check if we have all the essential fields
        return !TextUtils.isEmpty(getFullName()) &&
                !TextUtils.isEmpty(getEmergencyContactPhone()) &&
                !TextUtils.isEmpty(getState()) &&
                !TextUtils.isEmpty(getDateOfBirthString()) &&
                !TextUtils.isEmpty(getGender());
    }

    /**
     * Get the user's full name
     * @return Full name or empty string if not set
     */
    public String getFullName() {
        return sharedPreferences.getString(KEY_FULL_NAME, "");
    }

    /**
     * Get the user's first name
     * @return First name or empty string if not set
     */
    public String getFirstName() {
        return sharedPreferences.getString(KEY_FIRST_NAME, "");
    }

    /**
     * Get the user's last name
     * @return Last name or empty string if not set
     */
    public String getLastName() {
        return sharedPreferences.getString(KEY_LAST_NAME, "");
    }

    /**
     * Get the user's date of birth as a Date object
     * @return Date of birth or null if not set or invalid
     */
    public Date getDateOfBirth() {
        String dobString = sharedPreferences.getString(KEY_DATE_OF_BIRTH, null);
        if (dobString == null) return null;

        try {
            return storageFormat.parse(dobString);
        } catch (ParseException e) {
            Log.e(TAG, "Error parsing date of birth", e);
            return null;
        }
    }

    /**
     * Get the user's date of birth as a formatted string (DD-MM-YYYY)
     * @return Formatted date of birth or empty string if not set
     */
    public String getDateOfBirthString() {
        Date dob = getDateOfBirth();
        if (dob == null) return "";
        return displayFormat.format(dob);
    }

    /**
     * Set the user's date of birth from a string (DD-MM-YYYY)
     * @param dateString Date string in DD-MM-YYYY format
     */
    public void setDateOfBirth(String dateString) {
        if (TextUtils.isEmpty(dateString)) return;

        try {
            // Parse the display format (DD-MM-YYYY)
            Date date = displayFormat.parse(dateString);
            if (date != null) {
                // Store in YYYY-MM-DD format
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(KEY_DATE_OF_BIRTH, storageFormat.format(date));
                editor.apply();
            }
        } catch (ParseException e) {
            Log.e(TAG, "Error parsing date string: " + dateString, e);
        }
    }

    /**
     * Get the user's gender
     * @return Gender or empty string if not set
     */
    public String getGender() {
        return sharedPreferences.getString(KEY_GENDER, "");
    }

    /**
     * Set the user's gender
     * @param gender The gender to set
     */
    public void setGender(String gender) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_GENDER, gender);
        editor.apply();
    }

    /**
     * Get the user's state
     * @return State or empty string if not set
     */
    public String getState() {
        return sharedPreferences.getString(KEY_STATE, "");
    }

    /**
     * Set the user's state
     * @param state The state to set
     */
    public void setState(String state) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_STATE, state);
        editor.apply();
    }

    /**
     * Check if the user is a volunteer
     * @return true if volunteer, false otherwise
     */
    public boolean isVolunteer() {
        return sharedPreferences.getBoolean(KEY_IS_VOLUNTEER, false);
    }

    /**
     * Set the user's volunteer status
     * @param isVolunteer The volunteer status to set
     */
    public void setVolunteer(boolean isVolunteer) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_IS_VOLUNTEER, isVolunteer);
        editor.apply();
    }

    /**
     * Get the user's emergency contact phone number
     * @return Emergency contact or empty string if not set
     */
    public String getEmergencyContactPhone() {
        return sharedPreferences.getString(KEY_EMERGENCY_CONTACT, "");
    }

    /**
     * Set the user's emergency contact phone number
     * @param phone The emergency contact phone to set
     */
    public void setEmergencyContactPhone(String phone) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_EMERGENCY_CONTACT, formatPhoneNumber(phone));
        editor.apply();
    }

    /**
     * Set the user's full name
     * @param fullName The full name to set
     */
    public void setFullName(String fullName) {
        if (TextUtils.isEmpty(fullName)) return;

        // Parse full name into first and last name
        String firstName = fullName;
        String lastName = "";

        int spaceIndex = fullName.indexOf(" ");
        if (spaceIndex > 0) {
            firstName = fullName.substring(0, spaceIndex);
            lastName = fullName.substring(spaceIndex + 1);
        }

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_FULL_NAME, fullName);
        editor.putString(KEY_FIRST_NAME, firstName);
        editor.putString(KEY_LAST_NAME, lastName);
        editor.apply();
    }

    /**
     * Get a privacy preference setting
     * @param key Preference key
     * @param defaultValue Default value
     * @return Preference value or default if not set
     */
    public boolean getPrivacyPreference(String key, boolean defaultValue) {
        return privacyPreferences.getBoolean(key, defaultValue);
    }

    /**
     * Set a privacy preference setting
     * @param key Preference key
     * @param value Preference value
     */
    public void setPrivacyPreference(String key, boolean value) {
        privacyPreferences.edit().putBoolean(key, value).apply();
    }

    /**
     * Get an emergency preference setting
     * @param key Preference key
     * @param defaultValue Default value
     * @return Preference value or default if not set
     */
    public boolean getEmergencyPreference(String key, boolean defaultValue) {
        return emergencyPreferences.getBoolean(key, defaultValue);
    }

    /**
     * Set an emergency preference setting
     * @param key Preference key
     * @param value Preference value
     */
    public void setEmergencyPreference(String key, boolean value) {
        emergencyPreferences.edit().putBoolean(key, value).apply();
    }

    /**
     * Get a notification preference setting
     * @param key Preference key
     * @param defaultValue Default value
     * @return Preference value or default if not set
     */
    public boolean getNotificationPreference(String key, boolean defaultValue) {
        return notificationPreferences.getBoolean(key, defaultValue);
    }

    /**
     * Set a notification preference setting
     * @param key Preference key
     * @param value Preference value
     */
    public void setNotificationPreference(String key, boolean value) {
        notificationPreferences.edit().putBoolean(key, value).apply();
    }

    /**
     * Clear all session data (logout)
     */
    public void clearSession() {
        try {
            // Update offline status before clearing session
            updateOfflineStatus();

            // Sign out from Firebase
            firebaseAuth.signOut();

            // Clear all shared preferences
            sharedPreferences.edit().clear().apply();

            // Don't clear preferences as they should persist between logins
            // but may be custom cleared on user request in a settings page

            Log.d(TAG, "Session cleared successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing session", e);
        }
    }

    /**
     * Format phone number to ensure +91 prefix for India
     */
    private String formatPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return phoneNumber;
        }

        // Remove any non-digit characters except the + symbol
        String cleaned = phoneNumber.replaceAll("[^\\d+]", "");

        // Ensure it starts with +91
        if (!cleaned.startsWith("+")) {
            cleaned = "+91" + cleaned;
        } else if (!cleaned.startsWith("+91")) {
            cleaned = "+91" + cleaned.substring(1);
        }

        return cleaned;
    }
    public void saveUserProfileData(String firstName, String lastName, Date dateOfBirth,
                                    String state, String emergencyContact, boolean isVolunteer) {
        try {
            // Construct full name
            String fullName = firstName;
            if (lastName != null && !lastName.isEmpty()) {
                fullName += " " + lastName;
            }

            // Save to SharedPreferences
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(KEY_FULL_NAME, fullName);
            editor.putString(KEY_FIRST_NAME, firstName);
            editor.putString(KEY_LAST_NAME, lastName != null ? lastName : "");

            if (dateOfBirth != null) {
                editor.putString(KEY_DATE_OF_BIRTH, storageFormat.format(dateOfBirth));
            }

            editor.putString(KEY_STATE, state);
            editor.putString(KEY_EMERGENCY_CONTACT, formatPhoneNumber(emergencyContact));
            editor.putBoolean(KEY_IS_VOLUNTEER, isVolunteer);
            editor.putBoolean(KEY_PROFILE_COMPLETED, true);
            editor.apply();

            // Create or update User object in Firebase
            String phoneNumber = getSavedPhoneNumber();
            String userId = getUserId();

            if (phoneNumber != null && userId != null) {
                User user = new User();
                user.setUserId(userId);
                user.setPhoneNumber(phoneNumber);
                user.setFullName(fullName);
                user.setFirstName(firstName);
                user.setLastName(lastName != null ? lastName : "");
                user.setDateOfBirth(dateOfBirth);
                user.setState(state);
                user.setEmergencyContact(emergencyContact);
                user.setVolunteer(isVolunteer);

                // Update in Firebase
                userRepository.updateUserProfile(user, new OnCompleteListener() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "User profile data saved successfully");
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Error saving user profile data to Firebase", e);
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving user profile data", e);
        }
    }
}