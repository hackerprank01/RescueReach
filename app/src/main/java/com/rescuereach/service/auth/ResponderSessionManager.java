package com.rescuereach.service.auth;

import android.content.Context;
import android.content.SharedPreferences;

import com.rescuereach.data.model.Responder;
import com.rescuereach.data.repository.OnCompleteListener;
import com.rescuereach.data.repository.RepositoryProvider;
import com.rescuereach.data.repository.ResponderRepository;

/**
 * Manages responder session and provides common responder operations.
 */
public class ResponderSessionManager {
    private static final String PREF_NAME = "RescueReachResponderSession";
    private static final String KEY_RESPONDER_USERNAME = "responder_username";

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

    public void logout(final OnCompleteListener listener) {
        authService.signOut(new AuthService.AuthCallback() {
            @Override
            public void onSuccess() {
                currentResponder = null;
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.clear();
                editor.apply();
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