package com.rescuereach.service.backup;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.rescuereach.R;
import com.rescuereach.data.repository.OnCompleteListener;
import com.rescuereach.service.auth.UserSessionManager;
import com.rescuereach.service.background.BackupWorker;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages local backups of user data
 */
public class LocalBackupManager {

    private static final String TAG = "LocalBackupManager";
    private static final String BACKUP_FOLDER = "RescueReach/Backups";
    private static final String BACKUP_FILE_PREFIX = "rescuereach_backup_";

    private final Context context;
    private final UserSessionManager sessionManager;
    private final FirebaseFirestore db;
    private ScheduledExecutorService scheduler;

    public LocalBackupManager(Context context) {
        this.context = context.getApplicationContext();
        this.sessionManager = UserSessionManager.getInstance(context);
        this.db = FirebaseFirestore.getInstance();
    }

    /**
     * Create a backup of user data
     */
    public void createBackup(OnCompleteListener listener) {
        // Get user ID and phone number
        String userId = sessionManager.getUserId();
        String phoneNumber = sessionManager.getSavedPhoneNumber();

        if (userId == null || phoneNumber == null) {
            listener.onError(new Exception("User not logged in"));
            return;
        }

        // Create backup directory if it doesn't exist
        File backupDir = getBackupDirectory();
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            listener.onError(new Exception("Could not create backup directory"));
            return;
        }

        // Create backup file name with timestamp
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        String timestamp = dateFormat.format(new Date());
        File backupFile = new File(backupDir, BACKUP_FILE_PREFIX + timestamp + ".json");

        // Get user data from Firestore
        db.collection("users")
                .whereEqualTo("phoneNumber", phoneNumber)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        listener.onError(new Exception("User data not found"));
                        return;
                    }

                    try {
                        // Get the document
                        DocumentSnapshot document = queryDocumentSnapshots.getDocuments().get(0);
                        Map<String, Object> userData = document.getData();

                        // Create JSON object
                        JSONObject jsonData = new JSONObject();

                        // Add user preferences
                        JSONObject preferences = new JSONObject();
                        SharedPreferences prefs = context.getSharedPreferences(
                                "RescueReachUserSession", Context.MODE_PRIVATE);
                        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                            if (entry.getValue() instanceof String) {
                                preferences.put(entry.getKey(), (String) entry.getValue());
                            } else if (entry.getValue() instanceof Boolean) {
                                preferences.put(entry.getKey(), (Boolean) entry.getValue());
                            } else if (entry.getValue() instanceof Integer) {
                                preferences.put(entry.getKey(), (Integer) entry.getValue());
                            } else if (entry.getValue() instanceof Long) {
                                preferences.put(entry.getKey(), (Long) entry.getValue());
                            } else if (entry.getValue() instanceof Float) {
                                preferences.put(entry.getKey(), (Float) entry.getValue());
                            }
                        }

                        // Add Firestore data
                        JSONObject firestoreData = new JSONObject();
                        if (userData != null) {
                            for (Map.Entry<String, Object> entry : userData.entrySet()) {
                                if (entry.getValue() != null) {
                                    firestoreData.put(entry.getKey(), entry.getValue().toString());
                                }
                            }
                        }

                        // Add both to the main JSON object
                        jsonData.put("preferences", preferences);
                        jsonData.put("firestoreData", firestoreData);
                        jsonData.put("backupDate", System.currentTimeMillis());
                        jsonData.put("userId", userId);
                        jsonData.put("phoneNumber", phoneNumber);

                        // Write to file
                        FileOutputStream fos = new FileOutputStream(backupFile);
                        fos.write(jsonData.toString().getBytes());
                        fos.close();

                        // Update last backup timestamp
                        sessionManager.setLongPreference("last_backup_timestamp",
                                System.currentTimeMillis());

                        // Success
                        listener.onSuccess();
                    } catch (JSONException | IOException e) {
                        Log.e(TAG, "Error creating backup", e);
                        listener.onError(e);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching user data", e);
                    listener.onError(e);
                });
    }

    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10+ we use scoped storage
            return true;
        } else {
            return ContextCompat.checkSelfPermission(context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }
    /**
     * Restore from backup file
     */
    public void restoreFromBackup(File backupFile, OnCompleteListener listener) {
        try {
            // Read backup file
            FileInputStream fis = new FileInputStream(backupFile);
            StringBuilder stringBuilder = new StringBuilder();
            int content;
            while ((content = fis.read()) != -1) {
                stringBuilder.append((char) content);
            }
            fis.close();

            // Parse JSON
            JSONObject jsonData = new JSONObject(stringBuilder.toString());

            // Restore preferences
            JSONObject preferences = jsonData.getJSONObject("preferences");
            SharedPreferences.Editor editor = context.getSharedPreferences(
                    "RescueReachUserSession", Context.MODE_PRIVATE).edit();

            // Clear existing preferences first
            editor.clear();

            // Only restore non-critical preferences (do not restore auth tokens)
            for (int i = 0; i < preferences.names().length(); i++) {
                String key = preferences.names().getString(i);
                if (!key.contains("token") && !key.contains("auth")) {
                    Object value = preferences.get(key);
                    if (value instanceof String) {
                        editor.putString(key, (String) value);
                    } else if (value instanceof Boolean) {
                        editor.putBoolean(key, (Boolean) value);
                    } else if (value instanceof Integer) {
                        editor.putInt(key, (Integer) value);
                    } else if (value instanceof Long) {
                        editor.putLong(key, (Long) value);
                    } else if (value instanceof Float) {
                        editor.putFloat(key, (Float) value);
                    }
                }
            }
            editor.apply();

            // Success
            listener.onSuccess();

        } catch (IOException | JSONException e) {
            Log.e(TAG, "Error restoring backup", e);
            listener.onError(e);
        }
    }

    /**
     * Schedule automatic backups
     */
    public void scheduleAutoBackup() {
        PeriodicWorkRequest backupWork =
                new PeriodicWorkRequest.Builder(BackupWorker.class, 24, TimeUnit.HOURS)
                        .build();

        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                        "autoBackup",
                        ExistingPeriodicWorkPolicy.REPLACE,
                        backupWork);
    }

    /**
     * Cancel scheduled automatic backups
     */
    public void cancelAutoBackup() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    /**
     * Get backup directory
     */
    private File getBackupDirectory() {
        File externalDir = context.getExternalFilesDir(null);
        return new File(externalDir, BACKUP_FOLDER);
    }

    /**
     * Get all available backups
     */
    public File[] getAvailableBackups() {
        File backupDir = getBackupDirectory();
        if (!backupDir.exists()) {
            return new File[0];
        }

        return backupDir.listFiles((dir, name) ->
                name.startsWith(BACKUP_FILE_PREFIX) && name.endsWith(".json"));
    }

    /**
     * Get most recent backup
     */
    public File getMostRecentBackup() {
        File[] backups = getAvailableBackups();
        if (backups == null || backups.length == 0) {
            return null;
        }

        File mostRecent = backups[0];
        for (File backup : backups) {
            if (backup.lastModified() > mostRecent.lastModified()) {
                mostRecent = backup;
            }
        }

        return mostRecent;
    }
}