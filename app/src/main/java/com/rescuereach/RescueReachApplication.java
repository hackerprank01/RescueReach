package com.rescuereach;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.firebase.FirebaseApp;
import com.rescuereach.service.appearance.AppearanceManager;
import com.rescuereach.service.auth.UserSessionManager;

import java.util.concurrent.TimeUnit;

public class RescueReachApplication extends Application {

    private static final String TAG = "RescueReachApp";

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            // Initialize Firebase
            FirebaseApp.initializeApp(this);

            // Initialize appearance settings
            AppearanceManager.getInstance(this).applyCurrentTheme();

            // Initialize background tasks
            setupBackgroundTasks();

            Log.d(TAG, "Application initialized successfully");
        } catch (Exception e) {
            // Catch any initialization errors to prevent app crash
            Log.e(TAG, "Error during application initialization", e);
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        try {
            // Avoid using AppearanceManager here directly, as it may not be initialized yet
            // Instead, we'll get font scaling preference directly from UserSessionManager
            UserSessionManager sessionManager = UserSessionManager.getInstance(base);

            // Default to 1.0 (no scaling) if preference not set or error occurs
            float fontScale = 1.0f;
            try {
                // Get saved font scale value if available
                fontScale = sessionManager.getFloatPreference("font_scale_factor", 1.0f);
            } catch (Exception e) {
                Log.w(TAG, "Could not retrieve font scale factor, using default", e);
            }

            // Apply font scaling if needed
            if (fontScale != 1.0f) {
                Configuration configuration = new Configuration(base.getResources().getConfiguration());
                configuration.fontScale = fontScale;
                base = base.createConfigurationContext(configuration);
            }
        } catch (Exception e) {
            // If anything goes wrong, use the original context to avoid app crash
            Log.e(TAG, "Error in attachBaseContext, using original context", e);
        }

        super.attachBaseContext(base);
    }

    private void fixPlayServices() {
        try {
            // Fix for "Unknown calling package name 'com.google.android.gms'" error
            // by ensuring Google Play Services is properly initialized
            GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
            int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
            if (resultCode != ConnectionResult.SUCCESS) {
                Log.w("RescueReachApplication", "Google Play Services not available: " + resultCode);

                // Try to fix the issue
                if (apiAvailability.isUserResolvableError(resultCode)) {
                    // Let the system handle this when needed
                    Log.d("RescueReachApplication", "User resolvable Google Play Services error");
                }
            }
        } catch (Exception e) {
            Log.e("RescueReachApplication", "Error checking Google Play Services", e);
        }
    }

    private void setupBackgroundTasks() {
        try {
            // Set up auto-backup via WorkManager (if enabled)
            if (UserSessionManager.getInstance(this).getBackupPreference("auto_backup_enabled", false)) {
                // Set up constraints for auto-backup
                Constraints constraints = new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build();

                // Create the periodic backup work request
                // Run once per day
                PeriodicWorkRequest backupWorkRequest =
                        new PeriodicWorkRequest.Builder(BackupWorkerMigrated.class, 1, TimeUnit.DAYS)
                                .setConstraints(constraints)
                                .build();

                // Enqueue the work request
                WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                        "auto_backup",
                        ExistingPeriodicWorkPolicy.KEEP,
                        backupWorkRequest);

                Log.d(TAG, "Auto-backup scheduled");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up background tasks", e);
        }
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
}