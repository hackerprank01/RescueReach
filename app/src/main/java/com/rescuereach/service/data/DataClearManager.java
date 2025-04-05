package com.rescuereach.service.data;

import android.content.Context;
import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.rescuereach.data.repository.OnCompleteListener;
import com.rescuereach.service.auth.UserSessionManager;

import java.util.HashMap;

/**
 * Manages clearing user data while preserving authentication
 */
public class DataClearManager {

    private static final String TAG = "DataClearManager";

    private final Context context;
    private final UserSessionManager sessionManager;
    private final FirebaseFirestore db;
    private final DatabaseReference rtDatabase;

    public DataClearManager(Context context) {
        this.context = context.getApplicationContext();
        this.sessionManager = UserSessionManager.getInstance(context);
        this.db = FirebaseFirestore.getInstance();
        this.rtDatabase = FirebaseDatabase.getInstance().getReference();
    }

    /**
     * Clear user data from all sources except authentication
     * Keeps phone number intact
     */
    public void clearUserData(OnCompleteListener listener) {
        String userId = sessionManager.getUserId();
        String phoneNumber = sessionManager.getSavedPhoneNumber();

        if (userId == null || phoneNumber == null) {
            listener.onError(new Exception("User not logged in"));
            return;
        }

        // Format phone number for use as a key (remove special characters)
        String phoneKey = phoneNumber.replaceAll("[^\\d]", "");

        // Step 1: Clear Firestore data
        db.collection("users")
                .whereEqualTo("phoneNumber", phoneNumber)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        String documentId = queryDocumentSnapshots.getDocuments().get(0).getId();

                        // Update with minimal data (keep only phone number and userId)
                        db.collection("users")
                                .document(documentId)
                                .update(
                                        "fullName", "",
                                        "firstName", "",
                                        "lastName", "",
                                        "dateOfBirth", "",
                                        "gender", "",
                                        "state", "",
                                        "emergencyContact", "",
                                        "isVolunteer", false
                                )
                                .addOnSuccessListener(aVoid -> {
                                    // Step 2: Clear Realtime Database data
                                    clearRealtimeDatabase(phoneKey, userId, listener);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error clearing Firestore data", e);
                                    listener.onError(e);
                                });
                    } else {
                        // User not found in Firestore, still try to clear Realtime Database
                        clearRealtimeDatabase(phoneKey, userId, listener);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error finding user in Firestore", e);
                    listener.onError(e);
                });
    }

    /**
     * Clear data from Realtime Database
     */
    private void clearRealtimeDatabase(String phoneKey, String userId, OnCompleteListener listener) {
        HashMap<String, Object> updates = new HashMap<>();
        updates.put("fullName", "");
        updates.put("firstName", "");
        updates.put("lastName", "");
        updates.put("state", "");
        updates.put("emergencyContact", "");
        updates.put("isVolunteer", false);
        // Keep userId intact
        updates.put("userId", userId);

        rtDatabase.child("users").child(phoneKey)
                .updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    // Step 3: Clear SharedPreferences but keep phone number
                    clearSharedPreferences(listener);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error clearing Realtime Database data", e);
                    listener.onError(e);
                });
    }

    /**
     * Clear shared preferences but keep phone number
     */
    private void clearSharedPreferences(OnCompleteListener listener) {
        try {
            // Save the phone number
            String phoneNumber = sessionManager.getSavedPhoneNumber();

            // Clear all preferences
            sessionManager.clearAllUserData();

            // Restore phone number only
            sessionManager.saveUserPhoneNumber(phoneNumber);

            // Mark profile as incomplete
            sessionManager.resetProfileCompletionStatus();

            // Success
            listener.onSuccess();
        } catch (Exception e) {
            Log.e(TAG, "Error clearing SharedPreferences", e);
            listener.onError(e);
        }
    }
}