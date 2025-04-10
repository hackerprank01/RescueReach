package com.rescuereach.data.repository.firebase;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
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
    private final FirebaseAuth firebaseAuth;

    public FirebaseUserRepository() {
        this.firestore = FirebaseFirestore.getInstance();
        this.usersCollection = firestore.collection(COLLECTION_USERS);
        this.firebaseAuth = FirebaseAuth.getInstance();

        // Initialize date formatter with UTC timezone
        this.dateFormatter = new SimpleDateFormat(DATE_FORMAT_PATTERN, Locale.US);
        this.dateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    @Override
    public void getUserById(String userId, OnUserFetchedListener listener) {
        Log.d(TAG, "Getting user by ID: " + userId);

        if (userId == null || userId.isEmpty()) {
            listener.onError(new IllegalArgumentException("User ID cannot be null or empty"));
            return;
        }

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
        Log.d(TAG, "Getting user by phone number: " + phoneNumber);

        if (phoneNumber == null || phoneNumber.isEmpty()) {
            listener.onError(new IllegalArgumentException("Phone number cannot be null or empty"));
            return;
        }

        String formattedPhone = formatPhoneNumber(phoneNumber);
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

        // Check authentication state first
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            listener.onError(new IllegalStateException("User must be authenticated to save profile"));
            return;
        }

        // Set the userId to match the current user's Firebase Auth UID
        // This is critical for meeting the security rule requirements
        user.setUserId(currentUser.getUid());

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

        // Always include the authenticated user's UID
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null) {
            map.put("userId", currentUser.getUid());
        } else {
            map.put("userId", user.getUserId());
        }

        map.put("phoneNumber", user.getPhoneNumber());
        map.put("fullName", user.getFullName());
        map.put("emergencyContact", user.getEmergencyContact());
        map.put("createdAt", user.getCreatedAt());

        // Add formatted creation date
        if (user.getCreatedAt() != null) {
            map.put(FIELD_CREATED_AT_FORMATTED, dateFormatter.format(user.getCreatedAt()));
        }

        // For backward compatibility
        map.put("firstName", user.getFirstName());
        map.put("lastName", user.getLastName());

        // Add new fields
        if (user.getDateOfBirth() != null) {
            map.put("dateOfBirth", user.getDateOfBirth());
        }
        if (user.getGender() != null) {
            map.put("gender", user.getGender());
        }
        if (user.getState() != null) {
            map.put("state", user.getState());
        }
        map.put("isVolunteer", user.isVolunteer());

        return map;
    }

    @Override
    public void updateUserProfile(User user, OnCompleteListener listener) {
        Log.d(TAG, "Updating user profile: " + user.getPhoneNumber());

        // Check authentication state first
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            listener.onError(new IllegalStateException("User must be authenticated to update profile"));
            return;
        }

        // Critical: Ensure the user ID matches the authenticated user's UID
        // This is required by your security rules
        user.setUserId(currentUser.getUid());

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

        // Document ID is the formatted phone number
        DocumentReference userDoc = usersCollection.document(formattedPhone);

        Map<String, Object> updates = userToMap(user);

        // Using set with merge option instead of update
        // This can help with certain security rule configurations
        userDoc.set(updates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User profile updated successfully in Firestore");
                    updateUserInRealtimeDatabase(user, listener);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating user profile in Firestore", e);

                    // If using phone number as document ID fails,
                    // try to create/update a document with the user's UID as the document ID
                    DocumentReference userDocByUid = usersCollection.document(currentUser.getUid());
                    userDocByUid.set(updates, SetOptions.merge())
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "User profile updated successfully using UID as document ID");
                                updateUserInRealtimeDatabase(user, listener);
                            })
                            .addOnFailureListener(e2 -> {
                                Log.e(TAG, "Error updating with UID as document ID", e2);
                                listener.onError(e2);
                            });
                });
    }

    private void updateUserInRealtimeDatabase(User user, OnCompleteListener listener) {
        // Get current authenticated user
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();

        // Database reference
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");

        // Use phone number as key in Realtime Database (without +)
        String phoneKey = user.getPhoneNumber().replaceAll("[^\\d]", ""); // Remove non-digits

        Map<String, Object> updates = new HashMap<>();
        updates.put("fullName", user.getFullName() != null ? user.getFullName() : "");
        updates.put("emergencyContact", user.getEmergencyContact() != null ? user.getEmergencyContact() : "");

        // For backward compatibility
        updates.put("firstName", user.getFirstName() != null ? user.getFirstName() : "");
        updates.put("lastName", user.getLastName() != null ? user.getLastName() : "");

        // Add new fields
        if (user.getDateOfBirth() != null) {
            // Store dateOfBirth as formatted string to avoid complex object creation
            updates.put("dateOfBirth", dateFormatter.format(user.getDateOfBirth()).split(" ")[0]); // Only take the date part
        }

        if (user.getGender() != null) {
            updates.put("gender", user.getGender());
        }
        if (user.getState() != null) {
            updates.put("state", user.getState());
        }
        updates.put("isVolunteer", user.isVolunteer());

        // Add createdAtFormatted field
        if (user.getCreatedAt() != null) {
            updates.put("createdAtFormatted", dateFormatter.format(user.getCreatedAt()));
        }

        // Critical for security rules: Include the userId that matches the authenticated UID
        updates.put("userId", currentUser != null ? currentUser.getUid() : user.getUserId());

        // Try to update the existing entry first
        usersRef.child(phoneKey).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User profile updated successfully in Realtime Database");
                    listener.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating user in Realtime Database", e);

                    // If updating the phone-based entry fails, create a UID-based entry
                    if (currentUser != null) {
                        String userId = currentUser.getUid();

                        // Create a complete user entry with the UID as key
                        Map<String, Object> completeUserData = new HashMap<>(updates);
                        completeUserData.put("phoneNumber", user.getPhoneNumber());
                        completeUserData.put("status", "online");

                        usersRef.child(userId).setValue(completeUserData)
                                .addOnSuccessListener(innerVoid -> {
                                    Log.d(TAG, "User profile created with UID in Realtime Database");
                                    listener.onSuccess();
                                })
                                .addOnFailureListener(innerE -> {
                                    Log.e(TAG, "Failed to create UID entry in Realtime Database", innerE);
                                    // Consider operation successful if Firestore update worked
                                    listener.onSuccess();
                                });
                    } else {
                        // Still consider operation successful if Firestore update worked
                        listener.onSuccess();
                    }
                });
    }

    @Override
    public void deleteUser(String phoneNumber, OnCompleteListener listener) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            listener.onError(new IllegalArgumentException("Phone number cannot be null or empty"));
            return;
        }

        String formattedPhone = formatPhoneNumber(phoneNumber);
        usersCollection.document(formattedPhone).delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User deleted successfully from Firestore");

                    // Also delete from Realtime Database
                    String phoneKey = formattedPhone.replaceAll("[^\\d]", "");
                    FirebaseDatabase.getInstance().getReference("users").child(phoneKey).removeValue()
                            .addOnSuccessListener(innerVoid -> {
                                Log.d(TAG, "User deleted successfully from Realtime Database");
                                listener.onSuccess();
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error deleting user from Realtime Database", e);
                                // Still consider the operation successful if Firestore delete worked
                                listener.onSuccess();
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting user", e);
                    listener.onError(e);
                });
    }

    @Override
    public void getAllUsers(OnUserListFetchedListener listener) {
        usersCollection.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<User> userList = new ArrayList<>();
                    for (int i = 0; i < queryDocumentSnapshots.size(); i++) {
                        User user = queryDocumentSnapshots.getDocuments().get(i).toObject(User.class);
                        userList.add(user);
                    }
                    listener.onSuccess(userList);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting all users", e);
                    listener.onError(e);
                });
    }

    private void saveUserToRealtimeDatabase(User user, OnCompleteListener listener) {
        // Get current authenticated user
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            // No authenticated user, consider the operation successful anyway
            listener.onSuccess();
            return;
        }

        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");

        Map<String, Object> userBasicInfo = new HashMap<>();
        userBasicInfo.put("phoneNumber", user.getPhoneNumber());
        userBasicInfo.put("userId", currentUser.getUid());  // Use authenticated UID
        userBasicInfo.put("fullName", user.getFullName() != null ? user.getFullName() : "");
        userBasicInfo.put("firstName", user.getFirstName() != null ? user.getFirstName() : "");
        userBasicInfo.put("lastName", user.getLastName() != null ? user.getLastName() : "");
        userBasicInfo.put("emergencyContact", user.getEmergencyContact() != null ? user.getEmergencyContact() : "");
        userBasicInfo.put("status", "online");

        // Add createdAtFormatted field
        if (user.getCreatedAt() != null) {
            userBasicInfo.put("createdAtFormatted", dateFormatter.format(user.getCreatedAt()));
        }

        // Add new fields
        if (user.getDateOfBirth() != null) {
            // Store date of birth as a formatted string to prevent complex object creation
            userBasicInfo.put("dateOfBirth", dateFormatter.format(user.getDateOfBirth()).split(" ")[0]); // Only take the date part
        }

        if (user.getGender() != null) {
            userBasicInfo.put("gender", user.getGender());
        }
        if (user.getState() != null) {
            userBasicInfo.put("state", user.getState());
        }
        userBasicInfo.put("isVolunteer", user.isVolunteer());

        // Try both phone-key and UID approaches
        String phoneKey = user.getPhoneNumber().replaceAll("[^\\d]", ""); // Remove non-digits

        // First try with phone number as key
        usersRef.child(phoneKey).setValue(userBasicInfo)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User saved to Realtime Database with phone key");
                    listener.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving to Realtime Database with phone key", e);

                    // Try with UID as key instead
                    usersRef.child(currentUser.getUid()).setValue(userBasicInfo)
                            .addOnSuccessListener(innerVoid -> {
                                Log.d(TAG, "User saved to Realtime Database with UID key");
                                listener.onSuccess();
                            })
                            .addOnFailureListener(innerE -> {
                                Log.e(TAG, "Error saving to Realtime Database with UID key", innerE);
                                // Still consider operation successful if Firestore write worked
                                listener.onSuccess();
                            });
                });
    }

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