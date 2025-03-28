package com.rescuereach.service.auth;

import android.content.Context;
import android.content.SharedPreferences;

import com.rescuereach.data.model.User;
import com.rescuereach.data.repository.OnCompleteListener;
import com.rescuereach.data.repository.RepositoryProvider;
import com.rescuereach.data.repository.UserRepository;

public class UserSessionManager {
    private static final String PREF_NAME = "RescueReachUserSession";
    private static final String KEY_USER_PHONE = "user_phone";

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
        editor.putString(KEY_USER_PHONE, phoneNumber);
        editor.apply();
    }

    public String getSavedPhoneNumber() {
        return sharedPreferences.getString(KEY_USER_PHONE, null);
    }

    public void createNewUser(String phoneNumber, OnCompleteListener listener) {
        String userId = authService.getCurrentUserId();
        if (userId == null) {
            listener.onError(new Exception("User ID is null"));
            return;
        }

        User newUser = new User(userId, phoneNumber);
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

    // Other methods...
}