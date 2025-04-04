package com.rescuereach;

import android.app.Application;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.firebase.FirebaseApp;
import com.rescuereach.service.background.BackupWorker;
import com.rescuereach.service.auth.UserSessionManager;

import java.util.concurrent.TimeUnit;

public class RescueReachApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Firebase
        FirebaseApp.initializeApp(this);

        // Initialize background tasks
        setupBackgroundTasks();
    }

    private void setupBackgroundTasks() {
        // Set up constraints for auto-backup
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();

        // Create the periodic backup work request
        // Run once per day
        PeriodicWorkRequest backupWorkRequest =
                new PeriodicWorkRequest.Builder(BackupWorker.class, 1, TimeUnit.DAYS)
                        .setConstraints(constraints)
                        .build();

        // Enqueue the work request
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "auto_backup",
                ExistingPeriodicWorkPolicy.KEEP,
                backupWorkRequest);
    }
}