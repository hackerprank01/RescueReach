package com.rescuereach.service.messaging;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;
import com.rescuereach.service.auth.UserSessionManager;

/**
 * Manages Firebase Cloud Messaging (FCM) token generation and registration
 */
public class FCMManager {
    private static final String TAG = "FCMManager";

    private final Context context;
    private final UserSessionManager sessionManager;
    private final FirebaseFirestore firestore;

    public FCMManager(Context context) {
        this.context = context.getApplicationContext();
        this.sessionManager = UserSessionManager.getInstance(context);
        this.firestore = FirebaseFirestore.getInstance();
    }

    /**
     * Initialize FCM token management - call this from your Application class
     */
    public void initialize() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override
                    public void onComplete(@NonNull Task<String> task) {
                        if (!task.isSuccessful()) {
                            Log.e(TAG, "Failed to get FCM token", task.getException());
                            return;
                        }

                        // Get new FCM token and save it
                        String token = task.getResult();
                        saveFcmToken(token);
                    }
                });
    }

    /**
     * Save the FCM token to Firestore
     */
    private void saveFcmToken(String token) {
        if (token == null || token.isEmpty()) {
            Log.e(TAG, "Cannot save empty FCM token");
            return;
        }

        // Only save token if user is logged in
        String userId = sessionManager.getUserId();
        String phoneNumber = sessionManager.getSavedPhoneNumber();

        if (userId == null || phoneNumber == null) {
            Log.d(TAG, "User not logged in, skipping FCM token save");
            return;
        }

        // Save to both user document and a dedicated tokens collection
        firestore.collection("users").document(phoneNumber)
                .update("fcmToken", token)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "FCM token saved to user document"))
                .addOnFailureListener(e -> Log.e(TAG, "Error saving FCM token to user document", e));

        // Also save to tokens collection for easier lookup
        firestore.collection("fcm_tokens").document(userId)
                .set(new TokenData(token, phoneNumber, userId))
                .addOnSuccessListener(aVoid -> Log.d(TAG, "FCM token saved to tokens collection"))
                .addOnFailureListener(e -> Log.e(TAG, "Error saving to tokens collection", e));
    }

    /**
     * Utility class to store token data
     */
    private static class TokenData {
        public String token;
        public String phoneNumber;
        public String userId;
        public long timestamp;

        public TokenData(String token, String phoneNumber, String userId) {
            this.token = token;
            this.phoneNumber = phoneNumber;
            this.userId = userId;
            this.timestamp = System.currentTimeMillis();
        }
    }
}