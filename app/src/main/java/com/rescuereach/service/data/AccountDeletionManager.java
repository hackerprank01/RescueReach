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
    public void deleteUserAccount(OnCompleteListener listener) {
        String userId = sessionManager.getUserId();
        String phoneNumber = sessionManager.getSavedPhoneNumber();

        if (userId == null || phoneNumber == null) {
            listener.onError(new Exception("User not logged in"));
            return;
        }

        // Get current Firebase User
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            listener.onError(new Exception("User not authenticated"));
            return;
        }

        // Format phone number for use as a key (remove special characters)
        String phoneKey = phoneNumber.replaceAll("[^\\d]", "");

        // Step 1: Delete Firestore data
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
                                    // Step 2: Delete Realtime Database data
                                    deleteRealtimeData(phoneKey, user, listener);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error deleting Firestore data", e);
                                    listener.onError(e);
                                });
                    } else {
                        // User not found in Firestore, still try to delete Realtime Database data
                        deleteRealtimeData(phoneKey, user, listener);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error finding user in Firestore", e);
                    listener.onError(e);
                });
    }

    /**
     * Delete data from Realtime Database
     */
    private void deleteRealtimeData(String phoneKey, FirebaseUser user, OnCompleteListener listener) {
        rtDatabase.child("users").child(phoneKey)
                .removeValue()
                .addOnSuccessListener(aVoid -> {
                    // Step 3: Delete Firebase Authentication account
                    deleteAuthAccount(user, listener);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting Realtime Database data", e);
                    listener.onError(e);
                });
    }

    /**
     * Delete Firebase Authentication account
     */
    private void deleteAuthAccount(FirebaseUser user, OnCompleteListener listener) {

        if (user.getMetadata() != null &&
                System.currentTimeMillis() - user.getMetadata().getLastSignInTimestamp() > 5 * 60 * 1000) {
            // More than 5 minutes since last sign-in, require re-authentication
            listener.onError(new Exception("Please sign in again before deleting your account"));
            return;
        }

        user.delete()
                .addOnSuccessListener(aVoid -> {
                    // Step 4: Clear all local data
                    sessionManager.clearAllPreferences();

                    // Step 5: Sign out
                    FirebaseAuth.getInstance().signOut();

                    // Success
                    listener.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting Authentication account", e);

                    // If authentication deletion fails, still try to sign out
                    FirebaseAuth.getInstance().signOut();
                    sessionManager.clearAllPreferences();

                    listener.onError(e);
                });
    }
}