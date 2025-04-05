package com.rescuereach.service.data;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import androidx.core.content.FileProvider;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.rescuereach.R;
import com.rescuereach.data.repository.OnCompleteListener;
import com.rescuereach.data.repository.UserRepository;
import com.rescuereach.service.auth.UserSessionManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages data operations including backup, export, and account deletion
 */
public class DataManager {

    private static DataManager instance;
    private final Context context;
    private final UserSessionManager sessionManager;
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final Gson gson;
    private final UserRepository userRepository;

    private DataManager(Context context, UserRepository userRepository) {
        this.context = context.getApplicationContext();
        this.sessionManager = UserSessionManager.getInstance(context);
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
        this.userRepository = userRepository;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public static synchronized DataManager getInstance(Context context, UserRepository userRepository) {
        if (instance == null) {
            instance = new DataManager(context, userRepository);
        }
        return instance;
    }

    /**
     * Backup user data to Firebase
     */
    public void backupData(final OnBackupListener listener) {
        // Get user ID and phone number
        String userId = sessionManager.getUserId();
        String phoneNumber = sessionManager.getSavedPhoneNumber();

        if (userId == null || phoneNumber == null) {
            listener.onError(new Exception("User ID or phone number is missing"));
            return;
        }

        // Create a backup document with all user preferences
        Map<String, Object> backupData = new HashMap<>();

        // Add user info
        backupData.put("userId", userId);
        backupData.put("phoneNumber", phoneNumber);
        backupData.put("timestamp", new Date());

        // Add all preferences
        Map<String, Object> preferences = sessionManager.getAllPreferences();
        backupData.put("preferences", preferences);

        // Save to Firestore
        db.collection("userBackups")
                .document(userId)
                .set(backupData)
                .addOnSuccessListener(aVoid -> {
                    // Update backup timestamp
                    long currentTime = System.currentTimeMillis();
                    sessionManager.setLongPreference("last_backup_timestamp", currentTime);

                    listener.onSuccess(currentTime);
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Restore data from backup
     */
    public void restoreFromBackup(final OnRestoreListener listener) {
        // Get user ID
        String userId = sessionManager.getUserId();

        if (userId == null) {
            listener.onError(new Exception("User ID is missing"));
            return;
        }

        // Get backup from Firestore
        db.collection("userBackups")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Restore preferences
                        Map<String, Object> preferences =
                                (Map<String, Object>) documentSnapshot.get("preferences");

                        if (preferences != null) {
                            // Restore all preferences
                            sessionManager.restorePreferences(preferences);

                            // Update restore timestamp
                            long backupTimestamp = documentSnapshot.getDate("timestamp")
                                    .getTime();
                            listener.onSuccess(backupTimestamp);
                        } else {
                            listener.onError(new Exception("No preferences found in backup"));
                        }
                    } else {
                        listener.onError(new Exception("No backup found"));
                    }
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Export user data to a JSON file
     */
    public void exportUserData(final OnExportListener listener) {
        // Get user ID and phone number
        String userId = sessionManager.getUserId();
        String phoneNumber = sessionManager.getSavedPhoneNumber();

        if (userId == null || phoneNumber == null) {
            listener.onError(new Exception("User ID or phone number is missing"));
            return;
        }

        final JsonObject userData = new JsonObject();
        userData.addProperty("exportDate", new Date().toString());
        userData.addProperty("userId", userId);
        userData.addProperty("phoneNumber", phoneNumber);

        // Get user profile data
        db.collection("users")
                .whereEqualTo("phoneNumber", phoneNumber)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        // Get user data
                        DocumentSnapshot document = querySnapshot.getDocuments().get(0);
                        JsonObject profileData = new JsonObject();

                        // Add all fields to profileData
                        for (String key : document.getData().keySet()) {
                            Object value = document.getData().get(key);
                            if (value instanceof String) {
                                profileData.addProperty(key, (String) value);
                            } else if (value instanceof Boolean) {
                                profileData.addProperty(key, (Boolean) value);
                            } else if (value instanceof Number) {
                                profileData.addProperty(key, (Number) value);
                            } else {
                                profileData.addProperty(key, String.valueOf(value));
                            }
                        }

                        userData.add("profileData", profileData);

                        // Add preferences
                        Map<String, Object> preferences = sessionManager.getAllPreferences();
                        JsonObject preferencesJson = new JsonObject();

                        for (String key : preferences.keySet()) {
                            Object value = preferences.get(key);
                            if (value instanceof String) {
                                preferencesJson.addProperty(key, (String) value);
                            } else if (value instanceof Boolean) {
                                preferencesJson.addProperty(key, (Boolean) value);
                            } else if (value instanceof Number) {
                                preferencesJson.addProperty(key, (Number) value);
                            } else {
                                preferencesJson.addProperty(key, String.valueOf(value));
                            }
                        }

                        userData.add("preferences", preferencesJson);

                        // Get user emergencies
                        getEmergenciesAndFinishExport(userId, userData, listener);
                    } else {
                        // No user found, still export preferences
                        JsonObject preferencesJson = new JsonObject();
                        Map<String, Object> preferences = sessionManager.getAllPreferences();

                        for (String key : preferences.keySet()) {
                            Object value = preferences.get(key);
                            if (value instanceof String) {
                                preferencesJson.addProperty(key, (String) value);
                            } else if (value instanceof Boolean) {
                                preferencesJson.addProperty(key, (Boolean) value);
                            } else if (value instanceof Number) {
                                preferencesJson.addProperty(key, (Number) value);
                            } else {
                                preferencesJson.addProperty(key, String.valueOf(value));
                            }
                        }

                        userData.add("preferences", preferencesJson);

                        // Get user emergencies
                        getEmergenciesAndFinishExport(userId, userData, listener);
                    }
                })
                .addOnFailureListener(e -> {
                    // Export what we have even on failure
                    getEmergenciesAndFinishExport(userId, userData, listener);
                });
    }

    private void getEmergenciesAndFinishExport(String userId, JsonObject userData,
                                               OnExportListener listener) {
        // Get emergencies reported by the user
        db.collection("emergencies")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    // Create emergencies array
                    com.google.gson.JsonArray emergenciesArray = new com.google.gson.JsonArray();

                    // Add all emergencies
                    for (QueryDocumentSnapshot document : querySnapshot) {
                        JsonObject emergency = new JsonObject();

                        // Add all fields to emergency
                        for (String key : document.getData().keySet()) {
                            Object value = document.getData().get(key);
                            if (value instanceof String) {
                                emergency.addProperty(key, (String) value);
                            } else if (value instanceof Boolean) {
                                emergency.addProperty(key, (Boolean) value);
                            } else if (value instanceof Number) {
                                emergency.addProperty(key, (Number) value);
                            } else {
                                emergency.addProperty(key, String.valueOf(value));
                            }
                        }

                        emergenciesArray.add(emergency);
                    }

                    userData.add("emergencies", emergenciesArray);

                    // Save the data to a file and share it
                    saveAndShareExportFile(userData, listener);
                })
                .addOnFailureListener(e -> {
                    // Export what we have even on failure
                    saveAndShareExportFile(userData, listener);
                });
    }

    private void saveAndShareExportFile(JsonObject userData, OnExportListener listener) {
        try {
            // Create a unique filename with timestamp
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            String timestamp = sdf.format(new Date());
            String fileName = "rescue_reach_data_" + timestamp + ".json";

            // Get the export directory
            File exportDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "exports");
            if (!exportDir.exists()) {
                exportDir.mkdirs();
            }

            // Create the export file
            File exportFile = new File(exportDir, fileName);

            // Write the data to the file
            FileOutputStream fos = new FileOutputStream(exportFile);
            fos.write(gson.toJson(userData).getBytes());
            fos.close();

            // Create a share intent
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/json");

            // Get the file URI using FileProvider
            Uri fileUri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".fileprovider", exportFile);

            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.data_export_subject));
            shareIntent.putExtra(Intent.EXTRA_TEXT, context.getString(R.string.data_export_text));

            // Start the share activity
            Intent chooser = Intent.createChooser(shareIntent, context.getString(R.string.share_data_via));
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            context.startActivity(chooser);

            // Notify listener of success
            listener.onSuccess(exportFile.getAbsolutePath());
        } catch (IOException e) {
            listener.onError(e);
        }
    }

    /**
     * Clear all local data
     */
    public void clearLocalData(final OnCompleteListener listener) {
        // Clear all preferences except login info
        sessionManager.clearLocalData();

        // Notify listener of success
        listener.onSuccess();
    }

    /**
     * Delete user account from Firebase and clear all data
     */
    public void deleteUserAccount(final OnCompleteListener listener) {
        // Get current user
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            listener.onError(new Exception("Not logged in"));
            return;
        }

        // Get user phone number
        String phoneNumber = sessionManager.getSavedPhoneNumber();

        if (phoneNumber == null) {
            listener.onError(new Exception("Phone number is missing"));
            return;
        }

        // Counter to track completion of all deletion operations
        final AtomicInteger pendingOperations = new AtomicInteger(3); // Firestore user, auth, backups

        // Delete user document from Firestore
        userRepository.deleteUser(phoneNumber, new OnCompleteListener() {
            @Override
            public void onSuccess() {
                // Check if all operations are complete
                if (pendingOperations.decrementAndGet() == 0) {
                    finishDeletion(listener);
                }
            }

            @Override
            public void onError(Exception e) {
                // Continue with deletion even if this fails
                if (pendingOperations.decrementAndGet() == 0) {
                    finishDeletion(listener);
                }
            }
        });

        // Delete user from Firebase Auth
        user.delete()
                .addOnSuccessListener(aVoid -> {
                    // Check if all operations are complete
                    if (pendingOperations.decrementAndGet() == 0) {
                        finishDeletion(listener);
                    }
                })
                .addOnFailureListener(e -> {
                    // If this fails, we need to fail the entire operation
                    listener.onError(new Exception("Failed to delete auth account: " + e.getMessage()));
                });

        // Delete user backups
        db.collection("userBackups")
                .document(user.getUid())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    // Check if all operations are complete
                    if (pendingOperations.decrementAndGet() == 0) {
                        finishDeletion(listener);
                    }
                })
                .addOnFailureListener(e -> {
                    // Continue with deletion even if this fails
                    if (pendingOperations.decrementAndGet() == 0) {
                        finishDeletion(listener);
                    }
                });
    }

    private void finishDeletion(OnCompleteListener listener) {
        // Clear all local data
        sessionManager.clearSession();

        // Notify listener of success
        listener.onSuccess();
    }

    /**
     * Check if auto-backup is enabled
     */
    public boolean isAutoBackupEnabled() {
        return sessionManager.getDataPreference("auto_backup", true);
    }

    /**
     * Get last backup timestamp
     */
    public long getLastBackupTimestamp() {
        return sessionManager.getLongPreference("last_backup_timestamp", 0);
    }

    public interface OnBackupListener {
        void onSuccess(long timestamp);
        void onError(Exception e);
    }

    public interface OnRestoreListener {
        void onSuccess(long timestamp);
        void onError(Exception e);
    }

    public interface OnExportListener {
        void onSuccess(String filePath);
        void onError(Exception e);
    }
}