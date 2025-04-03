package com.rescuereach.service.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.rescuereach.data.model.Responder;
import com.rescuereach.data.repository.OnCompleteListener;
import com.rescuereach.data.repository.RepositoryProvider;
import com.rescuereach.data.repository.ResponderRepository;

/**
 * Manages responder session and provides common responder operations.
 */
public class ResponderSessionManager {
    private static final String TAG = "ResponderSessionManager";
    private static final String PREF_NAME = "RescueReachResponderSession";
    private static final String KEY_RESPONDER_USERNAME = "responder_username";
    private static final String KEY_RESPONDER_EMAIL = "responder_email";
    private static final String KEY_REMEMBER_ME = "remember_me";

    private static ResponderSessionManager instance;

    private final Context context;
    private final SharedPreferences sharedPreferences;
    private final AuthService authService;
    private final ResponderRepository responderRepository;

    private Responder currentResponder;

    private ResponderSessionManager(Context context) {
        this.context = context.getApplicationContext();
        this.sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.authService = AuthServiceProvider.getInstance().getAuthService();
        this.responderRepository = RepositoryProvider.getInstance().getResponderRepository();
    }

    public static synchronized ResponderSessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new ResponderSessionManager(context);
        }
        return instance;
    }

    public void saveEmail(String email) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_RESPONDER_EMAIL, email);
        editor.apply();
    }

    public String getSavedEmail() {
        return sharedPreferences.getString(KEY_RESPONDER_EMAIL, null);
    }

    public void clearSavedEmail() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_RESPONDER_EMAIL);
        editor.apply();
    }

    public void saveRememberMe(boolean remember) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_REMEMBER_ME, remember);
        editor.apply();
    }

    public boolean isRememberMeEnabled() {
        return sharedPreferences.getBoolean(KEY_REMEMBER_ME, false);
    }

    public void saveResponderUsername(String username) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_RESPONDER_USERNAME, username);
        editor.apply();
    }

    public String getSavedUsername() {
        return sharedPreferences.getString(KEY_RESPONDER_USERNAME, null);
    }

    public void loadCurrentResponder(final OnResponderLoadedListener listener) {
        if (!authService.isLoggedIn()) {
            listener.onError(new Exception("No responder logged in"));
            return;
        }

        final String responderId = authService.getCurrentUserId();
        if (responderId == null) {
            listener.onError(new Exception("Responder ID is null"));
            return;
        }

        responderRepository.getResponderById(responderId, new ResponderRepository.OnResponderFetchedListener() {
            @Override
            public void onSuccess(Responder responder) {
                currentResponder = responder;

                // Save username for later use
                if (responder != null && responder.getUsername() != null) {
                    saveResponderUsername(responder.getUsername());
                }

                listener.onResponderLoaded(responder);
            }

            @Override
            public void onError(Exception e) {
                listener.onError(e);
            }
        });
    }

    public Responder getCurrentResponder() {
        return currentResponder;
    }

    public void updateResponderLastLogin(Responder responder, final AuthService.AuthCallback callback) {
        if (responder == null) {
            callback.onError(new IllegalArgumentException("Responder cannot be null"));
            return;
        }

        responder.setLastLoginAt(new java.util.Date());

        responderRepository.updateResponder(responder, new OnCompleteListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Responder last login updated successfully");
                callback.onSuccess();
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Failed to update responder last login", e);
                callback.onError(e);
            }
        });
    }

    public void logout(final OnCompleteListener listener) {
        authService.signOut(new AuthService.AuthCallback() {
            @Override
            public void onSuccess() {
                currentResponder = null;
                // Only clear session data if remember me is disabled
                if (!isRememberMeEnabled()) {
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.clear();
                    editor.apply();
                } else {
                    // Keep email and remember me setting, but clear other session data
                    String savedEmail = getSavedEmail();
                    boolean rememberMe = isRememberMeEnabled();

                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.clear();
                    editor.putString(KEY_RESPONDER_EMAIL, savedEmail);
                    editor.putBoolean(KEY_REMEMBER_ME, rememberMe);
                    editor.apply();
                }
                listener.onSuccess();
            }

            @Override
            public void onError(Exception e) {
                listener.onError(e);
            }
        });
    }

    public interface OnResponderLoadedListener {
        void onResponderLoaded(Responder responder);
        void onError(Exception e);
    }
}