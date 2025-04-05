package com.rescuereach.service.data;

import android.content.Context;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.rescuereach.data.repository.OnCompleteListener;
import com.rescuereach.service.auth.UserSessionManager;

/**
 * Manages complete user account deletion
 */
public class AccountDeletionManager {

    private static final String TAG = "AccountDeletionManager";

    private final Context context;
    private final UserSessionManager sessionManager;
    private final FirebaseFirestore db;
    private final DatabaseReference rtDatabase;
    private final FirebaseAuth auth;

    public AccountDeletionManager(Context context) {
        this.context = context.getApplicationContext();
        this.sessionManager = UserSessionManager.getInstance(context);
        this.db = FirebaseFirestore.getInstance();
        this.rtDatabase = FirebaseDatabase.getInstance().getReference();
        this.auth = FirebaseAuth.getInstance();
    }

    /**
     * Delete user account and all associated data
     */
    public void deleteUserAccount(final OnCompleteListener listener) {
        // Get current user
        FirebaseUser user = auth.getCurrentUser();

        // Get user ID and phone number
        String userId = sessionManager.getUserId();
        String phoneNumber = sessionManager.getSavedPhoneNumber();

        if (user == null) {
            listener.onError(new Exception("User not authenticated"));
            return;
        }

        if (userId == null || phoneNumber == null) {
            listener.onError(new Exception("User ID or phone number is missing"));
            return;
        }

        // Format phone number for use as a key (remove special characters)
        String phoneKey = phoneNumber.replaceAll("[^\\d]", "");

        // First delete Firestore data
        deleteFirestoreData(user, phoneNumber, phoneKey, listener);
    }

    /**
     * Step 1: Delete data from Firestore
     */
    private void deleteFirestoreData(FirebaseUser user, String phoneNumber, String phoneKey, OnCompleteListener listener) {
        db.collection("users")
                .whereEqualTo("phoneNumber", phoneNumber)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        String documentId = queryDocumentSnapshots.getDocuments().get(0).getId();

                        // Delete document
                        db.collection("users")
                                .document(documentId)
                                .delete()
                                .addOnSuccessListener(aVoid -> {
                                    // Next delete realtime database data
                                    deleteRealtimeDatabase(user, phoneKey, listener);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error deleting Firestore data", e);
                                    // Continue with deletion anyway
                                    deleteRealtimeDatabase(user, phoneKey, listener);
                                });
                    } else {
                        // User not found in Firestore, still continue with other deletions
                        deleteRealtimeDatabase(user, phoneKey, listener);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error finding user in Firestore", e);
                    // Continue with deletion anyway
                    deleteRealtimeDatabase(user, phoneKey, listener);
                });
    }

    /**
     * Step 2: Delete data from Realtime Database
     */
    private void deleteRealtimeDatabase(FirebaseUser user, String phoneKey, OnCompleteListener listener) {
        rtDatabase.child("users").child(phoneKey)
                .removeValue()
                .addOnSuccessListener(aVoid -> {
                    // Next delete Firebase Auth account
                    deleteAuthAccount(user, listener);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting Realtime Database data", e);
                    // Continue with deletion anyway
                    deleteAuthAccount(user, listener);
                });
    }

    /**
     * Step 3: Delete Firebase Authentication account
     */
    private void deleteAuthAccount(FirebaseUser user, OnCompleteListener listener) {
        user.delete()
                .addOnSuccessListener(aVoid -> {
                    // Success - clear all local data and sign out
                    signOutAndClearData(listener);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting Authentication account", e);

                    // If this is a recent authentication error, notify the user
                    if (e.getMessage() != null && e.getMessage().contains("requires recent authentication")) {
                        listener.onError(new Exception("Please sign in again before deleting your account"));
                    } else {
                        // For other errors, still try to sign out
                        signOutAndClearData(listener);
                    }
                });
    }

    /**
     * Final step: Sign out and clear all local data
     */
    private void signOutAndClearData(OnCompleteListener listener) {
        try {
            // Clear all local data
            sessionManager.clearAllPreferences();

            // Sign out
            FirebaseAuth.getInstance().signOut();

            // Success
            listener.onSuccess();
        } catch (Exception e) {
            Log.e(TAG, "Error in signout/cleanup", e);
            listener.onError(e);
        }
    }
}