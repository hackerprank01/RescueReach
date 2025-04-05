package com.rescuereach.service.background;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.rescuereach.data.repository.firebase.FirebaseUserRepository;
import com.rescuereach.service.auth.UserSessionManager;
import com.rescuereach.service.data.DataManager;

public class BackupWorker extends Worker {
    private static final String TAG = "BackupWorker";

    public BackupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        UserSessionManager sessionManager = UserSessionManager.getInstance(context);
        DataManager dataManager = DataManager.getInstance(context, new FirebaseUserRepository());

        // Check if user is logged in
        if (!sessionManager.isLoggedIn()) {
            Log.d(TAG, "Auto-backup skipped: User not logged in");
            return Result.success();
        }

        // Check if auto-backup is enabled
        if (!dataManager.isAutoBackupEnabled()) {
            Log.d(TAG, "Auto-backup skipped: Feature disabled");
            return Result.success();
        }

        // Perform backup
        try {
            dataManager.backupData(new DataManager.OnBackupListener() {
                @Override
                public void onSuccess(long timestamp) {
                    Log.d(TAG, "Auto-backup successful at " + new java.util.Date(timestamp));
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Auto-backup failed: " + e.getMessage());
                }
            });

            // Return success regardless of backup outcome
            // (the listener just logs the result)
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Auto-backup exception: " + e.getMessage());
            return Result.retry();
        }
    }
}