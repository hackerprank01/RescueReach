package com.rescuereach.service.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.rescuereach.data.model.User;
import com.rescuereach.data.repository.OnCompleteListener;
import com.rescuereach.data.repository.RepositoryProvider;
import com.rescuereach.data.repository.UserRepository;

public class UserSessionManager {
    private static final String TAG = "UserSessionManager";
    private static final String PREF_NAME = "RescueReachUserSession";
    private static final String KEY_USER_PHONE = "user_phone";
    private static final String KEY_USER_FIRST_NAME = "user_first_name";
    private static final String KEY_USER_LAST_NAME = "user_last_name";
    private static final String KEY_USER_EMERGENCY_CONTACT = "user_emergency_contact";
    private static final String KEY_IS_PROFILE_COMPLETE = "is_profile_complete";

    private static UserSessionManager instance;

    private final Context context;
    private final SharedPreferences sharedPreferences;
    private final AuthService authService;
    private final UserRepository userRepository;

    private User currentUser;

    private UserSessionManager(Context context) {
        this.context = context.getApplicationContext();
        this.sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.authService = AuthServiceProvider.getInstance().getAuthService();
        this.userRepository = RepositoryProvider.getInstance().getUserRepository();
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

    public void saveUserProfileData(String firstName, String lastName, String emergencyContact) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_USER_FIRST_NAME, firstName);
        editor.putString(KEY_USER_LAST_NAME, lastName);
        editor.putString(KEY_USER_EMERGENCY_CONTACT, formatPhoneNumber(emergencyContact));
        editor.putBoolean(KEY_IS_PROFILE_COMPLETE, true);
        editor.apply();
    }

    public boolean isProfileComplete() {
        return sharedPreferences.getBoolean(KEY_IS_PROFILE_COMPLETE, false);
    }

    // New helper methods for ProfileFragment
    public String getFirstName() {
        return sharedPreferences.getString(KEY_USER_FIRST_NAME, "");
    }

    public String getLastName() {
        return sharedPreferences.getString(KEY_USER_LAST_NAME, "");
    }

    public String getFullName() {
        String firstName = getFirstName();
        String lastName = getLastName();

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

    public String getEmergencyContact() {
        return sharedPreferences.getString(KEY_USER_EMERGENCY_CONTACT, "");
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

    public void updateUserProfile(String firstName, String lastName, String emergencyContact, OnCompleteListener listener) {
        String userId = authService.getCurrentUserId();
        String phoneNumber = getSavedPhoneNumber();

        if (userId == null || phoneNumber == null) {
            listener.onError(new Exception("User ID or phone number is missing"));
            return;
        }

        User updatedUser = new User(
                userId,
                firstName,
                lastName,
                phoneNumber,
                formatPhoneNumber(emergencyContact)
        );

        userRepository.updateUserProfile(updatedUser, new OnCompleteListener() {
            @Override
            public void onSuccess() {
                currentUser = updatedUser;
                saveUserProfileData(firstName, lastName, emergencyContact);
                listener.onSuccess();
            }

            @Override
            public void onError(Exception e) {
                listener.onError(e);
            }
        });
    }
    public void markProfileComplete() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_IS_PROFILE_COMPLETE, true);
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
}