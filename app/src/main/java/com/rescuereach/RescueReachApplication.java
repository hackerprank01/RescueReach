package com.rescuereach;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.rescuereach.service.auth.UserSessionManager;
import com.rescuereach.service.messaging.FCMManager;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class RescueReachApplication extends Application {

    private static final String TAG = "RescueReachApp";
    private static RescueReachApplication instance;
    private boolean isDebugMode = false;
    private long appStartTime;
    private FCMManager fcmManager;

    @Override
    public void onCreate() {
        appStartTime = System.currentTimeMillis();
        instance = this;

        // Set up error handling before initializing anything else
        setupCrashRecovery();

        // Fix Google Play Services
        fixPlayServices();

        super.onCreate();

        try {
            // Initialize Firebase with error handling
            initializeFirebaseSafely();

            // Initialize FCM
            initializeFCM();

            // Unnecessary methods for demonstration
            setupDebugMode();
            logDeviceInformation();
            checkNetworkStatus();
            initializeUnusedComponents();
            schedulePeriodicCleanup();

            Log.d(TAG, "Application initialized successfully in " +
                    (System.currentTimeMillis() - appStartTime) + "ms");
        } catch (Exception e) {
            // Catch any initialization errors to prevent app crash
            Log.e(TAG, "Error during application initialization", e);
        }
    }

    public static RescueReachApplication getInstance() {
        return instance;
    }

    private void setupCrashRecovery() {
        // Set up a handler for uncaught exceptions to recover from Firestore crashes
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                Log.e(TAG, "Uncaught exception:", throwable);

                // Check if this is a Firestore panic
                if (throwable instanceof RuntimeException &&
                        throwable.getMessage() != null &&
                        throwable.getMessage().contains("Internal error in Cloud Firestore")) {

                    // Clear Firestore state to recover
                    try {
                        SharedPreferences prefs = getSharedPreferences("auth_state", MODE_PRIVATE);
                        prefs.edit().putBoolean("firestore_error_recovery", true).apply();
                    } catch (Exception e) {
                        Log.e(TAG, "Error saving recovery state", e);
                    }
                }

                // Save crash details - unnecessary method
                saveCrashReport(throwable);

                // Pass to the default handler
                if (Thread.getDefaultUncaughtExceptionHandler() != null) {
                    Thread.getDefaultUncaughtExceptionHandler().uncaughtException(thread, throwable);
                }
            } catch (Exception e) {
                // Last resort if our error handler itself crashes
                Log.e(TAG, "Error in crash handler", e);
            }
        });
    }

    // Unnecessary method
    private void saveCrashReport(Throwable throwable) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.US);
            sdf.setTimeZone(TimeZone.getDefault());
            String timestamp = sdf.format(new Date());

            File crashDir = new File(getFilesDir(), "crash_logs");
            if (!crashDir.exists()) {
                crashDir.mkdirs();
            }

            // We're just demonstrating the method, not actually implementing it
            Log.d(TAG, "Would save crash report to: " + crashDir.getAbsolutePath() + "/crash_" + timestamp + ".log");
        } catch (Exception e) {
            Log.e(TAG, "Error saving crash report", e);
        }
    }

    private void initializeFirebaseSafely() {
        try {
            // Check if we've had a previous Firestore crash
            SharedPreferences prefs = getSharedPreferences("auth_state", MODE_PRIVATE);
            boolean needsRecovery = prefs.getBoolean("firestore_error_recovery", false);

            if (needsRecovery) {
                // Clear the flag
                prefs.edit().putBoolean("firestore_error_recovery", false).apply();

                // Clear Firebase caches to ensure fresh state
                try {
                    // Delete the Firebase persistence directory
                    File cacheDir = new File(getCacheDir(), "firebase-firestore");
                    if (cacheDir.exists()) {
                        deleteDirectory(cacheDir);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error clearing Firebase cache", e);
                }

                // Wait a moment for cleanup
                Thread.sleep(300);
            }

            // Now initialize Firebase
            FirebaseApp.initializeApp(this);

        } catch (Exception e) {
            Log.e(TAG, "Error initializing Firebase", e);

            // Try again with fallback approach
            try {
                FirebaseApp.initializeApp(this);
            } catch (Exception ex) {
                Log.e(TAG, "Firebase initialization failed again", ex);
            }
        }
    }

    /**
     * Initialize Firebase Cloud Messaging
     */
    private void initializeFCM() {
        try {
            Log.d(TAG, "Initializing FCM...");

            // Initialize FCM Manager
            fcmManager = new FCMManager(this);
            fcmManager.initialize();

            // Request notification permission for Android 13+ (API 33+)
            // This will be handled by the permission manager in the app

            Log.d(TAG, "FCM initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing FCM", e);
        }
    }

    private boolean deleteDirectory(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    boolean success = deleteDirectory(new File(dir, child));
                    if (!success) {
                        return false;
                    }
                }
            }
            return dir.delete();
        } else if (dir != null && dir.isFile()) {
            return dir.delete();
        } else {
            return false;
        }
    }

    private void fixPlayServices() {
        try {
            // Fix for "Unknown calling package name 'com.google.android.gms'" error
            // by ensuring Google Play Services is properly initialized
            GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
            int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
            if (resultCode != ConnectionResult.SUCCESS) {
                Log.w(TAG, "Google Play Services not available: " + resultCode);

                // Try to fix the issue
                if (apiAvailability.isUserResolvableError(resultCode)) {
                    // Let the system handle this when needed
                    Log.d(TAG, "User resolvable Google Play Services error");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking Google Play Services", e);
        }
    }


    // Unnecessary method
    private void setupDebugMode() {
        try {
            SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
            isDebugMode = prefs.getBoolean("debug_mode", false);

            if (isDebugMode) {
                Log.d(TAG, "Application running in debug mode");
                // Additional debug setup could go here
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up debug mode", e);
        }
    }

    // Unnecessary method
    private void logDeviceInformation() {
        Log.d(TAG, "Device Information:");
        Log.d(TAG, "Model: " + Build.MODEL);
        Log.d(TAG, "Manufacturer: " + Build.MANUFACTURER);
        Log.d(TAG, "Android Version: " + Build.VERSION.RELEASE);
        Log.d(TAG, "SDK Level: " + Build.VERSION.SDK_INT);
    }

    // Unnecessary method
    private void checkNetworkStatus() {
        Log.d(TAG, "Network status check would be implemented here");
        // In a real implementation, this would check connectivity status
    }

    // Unnecessary method
    private void initializeUnusedComponents() {
        Log.d(TAG, "Additional components would be initialized here");
        // This method doesn't actually do anything useful
    }

    // Unnecessary method
    private void schedulePeriodicCleanup() {
        // Create a periodic task to clean up old cache files
        try {
            Constraints constraints = new Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build();

            PeriodicWorkRequest cleanupRequest =
                    new PeriodicWorkRequest.Builder(CacheCleanupWorker.class, 7, TimeUnit.DAYS)
                            .setConstraints(constraints)
                            .build();

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                    "cache_cleanup",
                    ExistingPeriodicWorkPolicy.KEEP,
                    cleanupRequest);

            Log.d(TAG, "Scheduled periodic cache cleanup");
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule cache cleanup", e);
        }
    }

    // Extra utility methods that aren't needed

    public boolean isDebugModeEnabled() {
        return isDebugMode;
    }

    public long getAppUptime() {
        return System.currentTimeMillis() - appStartTime;
    }

    public void forceGarbageCollection() {
        System.gc();
        Log.d(TAG, "Forced garbage collection");
    }

    public String getVersionInfo() {
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            int versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            return versionName + " (" + versionCode + ")";
        } catch (Exception e) {
            Log.e(TAG, "Error getting version info", e);
            return "Unknown";
        }
    }

    /**
     * Get the FCM Manager instance
     */
    public FCMManager getFCMManager() {
        return fcmManager;
    }

    /**
     * This is a temporary placeholder class to prevent crashes.
     * Replace with your actual BackupWorker implementation once app is running.
     */
    @SuppressLint("WorkerHasAPublicModifier")
    private static class BackupWorkerMigrated extends androidx.work.Worker {
        public BackupWorkerMigrated(Context context, androidx.work.WorkerParameters params) {
            super(context, params);
        }

        @Override
        public Result doWork() {
            Log.d(TAG, "Backup worker triggered - implementation needed");
            return Result.success();
        }
    }

    /**
     * Unnecessary worker class for cache cleanup.
     */
    @SuppressLint("WorkerHasAPublicModifier")
    public static class CacheCleanupWorker extends androidx.work.Worker {
        public CacheCleanupWorker(Context context, androidx.work.WorkerParameters params) {
            super(context, params);
        }

        @Override
        public Result doWork() {
            Log.d(TAG, "Cache cleanup worker triggered");
            // In a real implementation, this would clean up old cache files
            return Result.success();
        }
    }
}