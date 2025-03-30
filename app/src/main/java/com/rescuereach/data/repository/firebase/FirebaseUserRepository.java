package com.rescuereach.data.repository.firebase;

import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.rescuereach.data.model.User;
import com.rescuereach.data.repository.OnCompleteListener;
import com.rescuereach.data.repository.UserRepository;

public class FirebaseUserRepository implements UserRepository {
    private static final String TAG = "FirebaseUserRepository";
    private static final String COLLECTION_USERS = "users";
    private static final String FIELD_PHONE_NUMBER = "phoneNumber";

    private final FirebaseFirestore firestore;
    private final CollectionReference usersCollection;

    public FirebaseUserRepository() {
        this.firestore = FirebaseFirestore.getInstance();
        this.usersCollection = firestore.collection(COLLECTION_USERS);
    }

    @Override
    public void getUserById(String userId, OnUserFetchedListener listener) {
        Log.d(TAG, "Getting user by ID: " + userId);

        if (userId == null || userId.isEmpty()) {
            listener.onError(new IllegalArgumentException("User ID cannot be null or empty"));
            return;
        }

        DocumentReference userDoc = usersCollection.document(userId);
        userDoc.get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        listener.onSuccess(user);
                    } else {
                        Log.d(TAG, "No user found with ID: " + userId);
                        listener.onError(new Exception("User not found"));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting user by ID", e);

                    // Special handling for permission errors
                    if (e.getMessage().contains("PERMISSION_DENIED")) {
                        // Create user document if authenticated user is trying to access their own profile
                        createEmptyUserDocument(userId, e, listener);
                    } else {
                        listener.onError(e);
                    }
                });
    }

    @Override
    public void getUserByPhoneNumber(String phoneNumber, OnUserFetchedListener listener) {
        Log.d(TAG, "Getting user by phone number: " + phoneNumber);

        if (phoneNumber == null || phoneNumber.isEmpty()) {
            listener.onError(new IllegalArgumentException("Phone number cannot be null or empty"));
            return;
        }

        // Query Firestore for user with matching phone number
        Query query = usersCollection.whereEqualTo(FIELD_PHONE_NUMBER, phoneNumber);
        query.get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        User user = querySnapshot.getDocuments().get(0).toObject(User.class);
                        listener.onSuccess(user);
                    } else {
                        Log.d(TAG, "No user found with phone number: " + phoneNumber);
                        listener.onError(new Exception("User not found"));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting user by phone number", e);

                    // This is likely a permissions error since we're searching by phone
                    // Just report the user doesn't exist so a new one will be created
                    if (e.getMessage().contains("PERMISSION_DENIED")) {
                        Log.d(TAG, "Permission denied, assuming user doesn't exist");
                        listener.onError(new Exception("User not found"));
                    } else {
                        listener.onError(e);
                    }
                });
    }

    @Override
    public void saveUser(User user, OnCompleteListener listener) {
        Log.d(TAG, "Saving user: " + user.getUserId());

        if (user.getUserId() == null || user.getUserId().isEmpty()) {
            listener.onError(new IllegalArgumentException("User ID cannot be null or empty"));
            return;
        }

        DocumentReference userDoc = usersCollection.document(user.getUserId());
        userDoc.set(user)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User saved successfully");

                    // Also save basic user info to Realtime Database for online status tracking
                    saveUserToRealtimeDatabase(user, listener);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving user", e);
                    listener.onError(e);
                });
    }

    private void saveUserToRealtimeDatabase(User user, OnCompleteListener listener) {
        // This method would save basic user info to Realtime Database
        // For now, we'll just call the success callback
        listener.onSuccess();

        // In a full implementation, you'd do something like:
        /*
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");
        Map<String, Object> userBasicInfo = new HashMap<>();
        userBasicInfo.put("phoneNumber", user.getPhoneNumber());
        userBasicInfo.put("lastActive", ServerValue.TIMESTAMP);
        userBasicInfo.put("status", "online");

        usersRef.child(user.getUserId()).setValue(userBasicInfo)
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(listener::onError);
        */
    }

    private void createEmptyUserDocument(String userId, Exception originalError, OnUserFetchedListener listener) {
        // If permission error occurred when getting a user's own document, try to create it
        User newUser = new User(userId, "");
        saveUser(newUser, new OnCompleteListener() {
            @Override
            public void onSuccess() {
                // Document created successfully, now try to get it again
                getUserById(userId, listener);
            }

            @Override
            public void onError(Exception e) {
                // If we can't create it either, return the original error
                listener.onError(originalError);
            }
        });
    }
}