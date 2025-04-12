package com.rescuereach;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.firebase.FirebaseApp;
import com.onesignal.OneSignal;
import com.rescuereach.service.auth.UserSessionManager;
import com.rescuereach.service.notification.OneSignalManager;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class RescueReachApplication extends Application {

    private static final String TAG = "RescueReachApp";
    private static final String ONESIGNAL_APP_ID = "YOUR-ONESIGNAL-APP-ID"; // Replace with your actual App ID

    private static RescueReachApplication instance;
    private boolean isDebugMode = false;
    private long appStartTime;
    private OneSignalManager oneSignalManager;

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

            // Initialize OneSignal (instead of FCM)
            initializeOneSignal();

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

    /**
     * Initialize OneSignal for push notifications and SMS
     */
    private void initializeOneSignal() {
        try {
            Log.d(TAG, "Initializing OneSignal...");

            // OneSignal Initialization
            OneSignal.initWithContext(this);
            OneSignal.setAppId(ONESIGNAL_APP_ID);

            // Enable verbose logging in debug mode
            if (isDebugMode) {
                OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);
            }

            // Initialize our manager class
            oneSignalManager = new OneSignalManager(this);

            // Request notification permission
            OneSignal.promptForPushNotifications();

            // Set external user id if user is logged in
            UserSessionManager sessionManager = UserSessionManager.getInstance(this);
            if (sessionManager.isLoggedIn()) {
                String userId = sessionManager.getUserId();
                if (userId != null && !userId.isEmpty()) {
                    oneSignalManager.setUserId(userId);
                }
            }

            Log.d(TAG, "OneSignal initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing OneSignal", e);
        }
    }

    // Keep all your existing methods...

    private void setupCrashRecovery() {
        // Existing implementation
    }

    private void saveCrashReport(Throwable throwable) {
        // Existing implementation
    }

    private void initializeFirebaseSafely() {
        // Existing implementation - still needed for other Firebase services
    }

    private boolean deleteDirectory(File dir) {
        // Existing implementation
    }

    private void fixPlayServices() {
        // Existing implementation
    }

    private void setupDebugMode() {
        // Existing implementation
    }

    private void logDeviceInformation() {
        // Existing implementation
    }

    private void checkNetworkStatus() {
        // Existing implementation
    }

    private void initializeUnusedComponents() {
        // Existing implementation
    }

    private void schedulePeriodicCleanup() {
        // Existing implementation
    }

    public boolean isDebugModeEnabled() {
        // Existing implementation
        return isDebugMode;
    }

    public long getAppUptime() {
        // Existing implementation
        return System.currentTimeMillis() - appStartTime;
    }

    /**
     * Get the OneSignal manager instance
     */
    public OneSignalManager getOneSignalManager() {
        return oneSignalManager;
    }

    // Keep remaining existing methods and inner classes...
}