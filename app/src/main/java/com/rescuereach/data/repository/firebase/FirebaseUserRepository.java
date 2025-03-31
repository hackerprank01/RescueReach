package com.rescuereach.data.repository.firebase;

import android.util.Log;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.rescuereach.data.model.User;
import com.rescuereach.data.repository.OnCompleteListener;
import com.rescuereach.data.repository.UserRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class FirebaseUserRepository implements UserRepository {
    private static final String TAG = "FirebaseUserRepository";
    private static final String COLLECTION_USERS = "users";
    private static final String FIELD_PHONE_NUMBER = "phoneNumber";
    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_CREATED_AT_FORMATTED = "createdAtFormatted";
    private static final String DATE_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final FirebaseFirestore firestore;
    private final CollectionReference usersCollection;
    private final SimpleDateFormat dateFormatter;

    public FirebaseUserRepository() {
        this.firestore = FirebaseFirestore.getInstance();
        this.usersCollection = firestore.collection(COLLECTION_USERS);

        // Initialize date formatter with UTC timezone
        this.dateFormatter = new SimpleDateFormat(DATE_FORMAT_PATTERN, Locale.US);
        this.dateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    @Override
    public void getUserById(String userId, OnUserFetchedListener listener) {
        // Existing implementation
        Log.d(TAG, "Getting user by ID: " + userId);

        if (userId == null || userId.isEmpty()) {
            listener.onError(new IllegalArgumentException("User ID cannot be null or empty"));
            return;
        }

        // Now we need to query by userId field, not document ID
        Query query = usersCollection.whereEqualTo(FIELD_USER_ID, userId);
        query.get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        User user = querySnapshot.getDocuments().get(0).toObject(User.class);
                        listener.onSuccess(user);
                    } else {
                        Log.d(TAG, "No user found with ID: " + userId);
                        listener.onError(new Exception("User not found"));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting user by ID", e);
                    listener.onError(e);
                });
    }

    @Override
    public void getUserByPhoneNumber(String phoneNumber, OnUserFetchedListener listener) {
        // Existing implementation
        Log.d(TAG, "Getting user by phone number: " + phoneNumber);

        if (phoneNumber == null || phoneNumber.isEmpty()) {
            listener.onError(new IllegalArgumentException("Phone number cannot be null or empty"));
            return;
        }

        // Format phone number to ensure +91 prefix
        String formattedPhone = formatPhoneNumber(phoneNumber);

        // Document ID is now the formatted phone number
        DocumentReference userDoc = usersCollection.document(formattedPhone);
        userDoc.get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        listener.onSuccess(user);
                    } else {
                        Log.d(TAG, "No user found with phone number: " + formattedPhone);
                        listener.onError(new Exception("User not found"));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting user by phone number", e);
                    listener.onError(e);
                });
    }

    @Override
    public void saveUser(User user, OnCompleteListener listener) {
        Log.d(TAG, "Saving user: " + user.getPhoneNumber());

        if (user.getUserId() == null || user.getUserId().isEmpty()) {
            listener.onError(new IllegalArgumentException("User ID cannot be null or empty"));
            return;
        }

        if (user.getPhoneNumber() == null || user.getPhoneNumber().isEmpty()) {
            listener.onError(new IllegalArgumentException("Phone number cannot be null or empty"));
            return;
        }

        // Format phone number to ensure +91 prefix
        String formattedPhone = formatPhoneNumber(user.getPhoneNumber());
        user.setPhoneNumber(formattedPhone);

        // Ensure createdAt is set
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(new Date());
        }

        // Add formatted timestamp to the user object
        Map<String, Object> userMap = userToMap(user);
        userMap.put(FIELD_CREATED_AT_FORMATTED, dateFormatter.format(user.getCreatedAt()));

        // Document ID is now the formatted phone number
        DocumentReference userDoc = usersCollection.document(formattedPhone);
        userDoc.set(userMap)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User saved successfully to Firestore");
                    // Also save basic user info to Realtime Database for online status tracking
                    saveUserToRealtimeDatabase(user, listener);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving user to Firestore", e);
                    listener.onError(e);
                });
    }

    private Map<String, Object> userToMap(User user) {
        Map<String, Object> map = new HashMap<>();
        map.put("userId", user.getUserId());
        map.put("phoneNumber", user.getPhoneNumber());
        map.put("firstName", user.getFirstName());
        map.put("lastName", user.getLastName());
        map.put("emergencyContact", user.getEmergencyContact());
        map.put("createdAt", user.getCreatedAt());

        return map;
    }

    @Override
    public void updateUserProfile(User user, OnCompleteListener listener) {
        // ... existing implementation
        Log.d(TAG, "Updating user profile: " + user.getPhoneNumber());

        if (user.getPhoneNumber() == null || user.getPhoneNumber().isEmpty()) {
            listener.onError(new IllegalArgumentException("Phone number cannot be null or empty"));
            return;
        }

        // Format phone number to ensure +91 prefix
        String formattedPhone = formatPhoneNumber(user.getPhoneNumber());
        user.setPhoneNumber(formattedPhone);

        // Format emergency contact if provided
        if (user.getEmergencyContact() != null && !user.getEmergencyContact().isEmpty()) {
            user.setEmergencyContact(formatPhoneNumber(user.getEmergencyContact()));
        }

        // Document ID is now the formatted phone number
        DocumentReference userDoc = usersCollection.document(formattedPhone);

        Map<String, Object> updates = new HashMap<>();
        updates.put("userId", user.getUserId());
        updates.put("firstName", user.getFirstName());
        updates.put("lastName", user.getLastName());
        updates.put("emergencyContact", user.getEmergencyContact());

        // Also update the Realtime Database with the new information
        userDoc.update(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User profile updated successfully in Firestore");
                    // Update the Realtime Database as well
                    updateUserInRealtimeDatabase(user, listener);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating user profile in Firestore", e);
                    listener.onError(e);
                });
    }

    @Override
    public void deleteUser(String phoneNumber, OnCompleteListener listener) {
        Log.d(TAG, "Deleting user with phone: " + phoneNumber);

        if (phoneNumber == null || phoneNumber.isEmpty()) {
            listener.onError(new IllegalArgumentException("Phone number cannot be null or empty"));
            return;
        }

        // Format phone number to ensure +91 prefix
        String formattedPhone = formatPhoneNumber(phoneNumber);

        // Document ID is now the formatted phone number
        DocumentReference userDoc = usersCollection.document(formattedPhone);
        userDoc.delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User deleted successfully");
                    listener.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting user", e);
                    listener.onError(e);
                });
    }

    @Override
    public void getAllUsers(OnUserListFetchedListener listener) {
        Log.d(TAG, "Getting all users");

        usersCollection.get()
                .addOnSuccessListener(querySnapshot -> {
                    List<User> users = new ArrayList<>();
                    for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                        User user = document.toObject(User.class);
                        if (user != null) {
                            users.add(user);
                        }
                    }
                    listener.onSuccess(users);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting all users", e);
                    listener.onError(e);
                });
    }

    private void updateUserInRealtimeDatabase(User user, OnCompleteListener listener) {
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");

        // Use phone number as key in realtime database
        String phoneKey = user.getPhoneNumber().replaceAll("[^\\d]", ""); // Remove non-digits

        Map<String, Object> updates = new HashMap<>();
        updates.put("firstName", user.getFirstName() != null ? user.getFirstName() : "");
        updates.put("lastName", user.getLastName() != null ? user.getLastName() : "");
        updates.put("fullName", user.getFullName());
        updates.put("emergencyContact", user.getEmergencyContact() != null ? user.getEmergencyContact() : "");
        updates.put("lastUpdated", ServerValue.TIMESTAMP);

        usersRef.child(phoneKey).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User profile updated successfully in Realtime Database");
                    listener.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating user in Realtime Database", e);
                    // Consider operation successful if Firestore update worked
                    listener.onSuccess();
                });
    }

    private void saveUserToRealtimeDatabase(User user, OnCompleteListener listener) {
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");
        Map<String, Object> userBasicInfo = new HashMap<>();
        userBasicInfo.put("phoneNumber", user.getPhoneNumber());
        userBasicInfo.put("userId", user.getUserId());
        userBasicInfo.put("firstName", user.getFirstName() != null ? user.getFirstName() : "");
        userBasicInfo.put("lastName", user.getLastName() != null ? user.getLastName() : "");
        userBasicInfo.put("fullName", user.getFullName());
        userBasicInfo.put("emergencyContact", user.getEmergencyContact() != null ? user.getEmergencyContact() : "");
        userBasicInfo.put("lastActive", ServerValue.TIMESTAMP);
        userBasicInfo.put("createdAt", ServerValue.TIMESTAMP);
        userBasicInfo.put("status", "online");

        // Use phone number as key in realtime database too
        String phoneKey = user.getPhoneNumber().replaceAll("[^\\d]", ""); // Remove non-digits
        usersRef.child(phoneKey).setValue(userBasicInfo)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User saved successfully to Realtime Database");

                    // After the timestamp is written, retrieve it and save the formatted version
                    usersRef.child(phoneKey).child("createdAt").get()
                            .addOnSuccessListener(dataSnapshot -> {
                                if (dataSnapshot.exists()) {
                                    Long timestamp = dataSnapshot.getValue(Long.class);
                                    if (timestamp != null) {
                                        String formattedDate = formatTimestamp(new Date(timestamp));
                                        usersRef.child(phoneKey).child("createdAtFormatted").setValue(formattedDate);
                                    }
                                }
                                listener.onSuccess();
                            })
                            .addOnFailureListener(e -> listener.onSuccess()); // Still consider successful
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving to Realtime Database", e);
                    // Still consider the operation successful if Firestore write worked
                    listener.onSuccess();
                });
    }

    // Other methods remain the same...

    // Format timestamp to readable date time string in 24-hour format
    public String formatTimestamp(Date timestamp) {
        if (timestamp == null) return "Unknown";
        return dateFormatter.format(timestamp);
    }

    // Ensure phone number is properly formatted with +91 prefix
    private String formatPhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return null;

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
}