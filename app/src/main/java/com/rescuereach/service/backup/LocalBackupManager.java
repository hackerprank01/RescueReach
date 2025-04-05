package com.rescuereach.service.backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.rescuereach.data.repository.OnCompleteListener;
import com.rescuereach.service.auth.UserSessionManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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

                        Map<String, ?> allPrefs = prefs.getAll();
                        for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
                            if (entry.getValue() != null) {
                                preferences.put(entry.getKey(), entry.getValue().toString());
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

    /**
     * Schedule automatic backups using WorkManager (preferred over ScheduledExecutorService)
     */
    public void scheduleAutoBackup() {
        // Cancel any existing scheduled backups first
        cancelAutoBackup();

        try {
            // Set up constraints - only when on unmetered network and not low battery
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .build();

            // Schedule backup to run once per day
            PeriodicWorkRequest backupWorkRequest =
                    new PeriodicWorkRequest.Builder(BackupWorkerCompat.class, 24, TimeUnit.HOURS)
                            .setConstraints(constraints)
                            .setInitialDelay(1, TimeUnit.HOURS) // Start after a delay
                            .build();

            // Enqueue the work request
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "auto_backup",
                    ExistingPeriodicWorkPolicy.REPLACE,
                    backupWorkRequest);

            Log.d(TAG, "Auto backup scheduled successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling auto backup", e);

            // Fallback to using ScheduledExecutorService
            fallbackScheduleAutoBackup();
        }
    }

    /**
     * Fallback scheduler using ScheduledExecutorService (if WorkManager fails)
     */
    private void fallbackScheduleAutoBackup() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            createBackup(new OnCompleteListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Auto backup successful (fallback method)");
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Auto backup failed (fallback method)", e);
                }
            });
        }, 1, 24, TimeUnit.HOURS); // Start after 1 hour, repeat every 24 hours
    }

    /**
     * Cancel scheduled automatic backups
     */
    public void cancelAutoBackup() {
        // Cancel WorkManager tasks
        try {
            WorkManager.getInstance(context).cancelUniqueWork("auto_backup");
        } catch (Exception e) {
            Log.e(TAG, "Error canceling WorkManager backup", e);
        }

        // Also cancel the fallback scheduler if running
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

    /**
     * Compatible BackupWorker implementation
     */
    public static class BackupWorkerCompat extends androidx.work.Worker {
        private final Context context;

        public BackupWorkerCompat(Context context, androidx.work.WorkerParameters params) {
            super(context, params);
            this.context = context;
        }

        @Override
        public Result doWork() {
            LocalBackupManager backupManager = new LocalBackupManager(context);

            final boolean[] success = {false};
            final Exception[] error = {null};

            // Create synchronous version to work with WorkManager
            backupManager.createBackup(new OnCompleteListener() {
                @Override
                public void onSuccess() {
                    success[0] = true;
                }

                @Override
                public void onError(Exception e) {
                    success[0] = false;
                    error[0] = e;
                }
            });

            if (success[0]) {
                return Result.success();
            } else {
                return Result.failure();
            }
        }
    }
}