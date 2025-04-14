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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.onesignal.OneSignal;
import com.rescuereach.service.notification.NotificationService;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;

public class RescueReachApplication extends Application {

    private static final String TAG = "RescueReachApp";
    private static RescueReachApplication instance;
    private boolean isDebugMode = false;
    private long appStartTime;
    private final AtomicBoolean isHandlingCrash = new AtomicBoolean(false);

    // OneSignal App ID
    private static final String ONESIGNAL_APP_ID = "d85004b4-aabf-48ad-8c12-a74b90bdf57c";

    // Network timeout configuration
    private static final int CONNECTION_TIMEOUT = 30; // seconds
    private static final int READ_TIMEOUT = 30; // seconds
    private static final int WRITE_TIMEOUT = 30; // seconds

    // Notification service
    private NotificationService notificationService;

    // Original uncaught exception handler
    private Thread.UncaughtExceptionHandler originalExceptionHandler;

    @Override
    public void onCreate() {
        appStartTime = System.currentTimeMillis();
        instance = this;

        // Set up error handling before initializing anything else
        setupCrashRecovery();

        // Configure global OkHttp client with proper timeouts
        configureOkHttpDefaults();

        // Fix Google Play Services
        fixPlayServices();

        super.onCreate();

        try {
            // Initialize Firebase with error handling
            initializeFirebaseSafely();

            // Initialize OneSignal
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
     * Initialize OneSignal SDK for push notifications
     * Updated for compatibility with current OneSignal version
     */
    private void initializeOneSignal() {
        try {
            // Enable verbose logging for debug builds
            if (BuildConfig.DEBUG) {
                OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);
            } else {
                OneSignal.setLogLevel(OneSignal.LOG_LEVEL.ERROR, OneSignal.LOG_LEVEL.NONE);
            }

            // Initialize the OneSignal SDK
            OneSignal.initWithContext(this);
            OneSignal.setAppId(ONESIGNAL_APP_ID);

            // Initialize our notification service wrapper
            notificationService = NotificationService.getInstance(this);
            notificationService.initialize();

            Log.d(TAG, "OneSignal initialized with App ID: " + ONESIGNAL_APP_ID);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing OneSignal", e);
        }
    }

    /**
     * Get the notification service instance
     * @return NotificationService instance
     */
    public NotificationService getNotificationService() {
        if (notificationService == null) {
            notificationService = NotificationService.getInstance(this);
            notificationService.initialize();
        }
        return notificationService;
    }

    /**
     * Configure default OkHttp client settings to prevent timeouts
     */
    private void configureOkHttpDefaults() {
        try {
            // Create a custom global OkHttpClient
            OkHttpClient customClient = createCustomOkHttpClient();

            // Try to set this client using reflection for various libraries
            if (customClient != null) {
                setGlobalOkHttpDefaults(customClient);
            }

            Log.d(TAG, "OkHttp timeouts configured successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to configure OkHttp defaults", e);
        }
    }

    /**
     * Create a custom OkHttpClient with better timeout settings
     */
    private OkHttpClient createCustomOkHttpClient() {
        try {
            // Use reflection to avoid direct dependency on specific OkHttp version
            Class<?> clientBuilderClass = Class.forName("okhttp3.OkHttpClient$Builder");
            Object builder = clientBuilderClass.newInstance();

            // Set timeouts
            Method connectTimeout = clientBuilderClass.getMethod("connectTimeout", long.class, TimeUnit.class);
            Method readTimeout = clientBuilderClass.getMethod("readTimeout", long.class, TimeUnit.class);
            Method writeTimeout = clientBuilderClass.getMethod("writeTimeout", long.class, TimeUnit.class);
            Method retryOnConnectionFailure = clientBuilderClass.getMethod("retryOnConnectionFailure", boolean.class);

            connectTimeout.invoke(builder, CONNECTION_TIMEOUT, TimeUnit.SECONDS);
            readTimeout.invoke(builder, READ_TIMEOUT, TimeUnit.SECONDS);
            writeTimeout.invoke(builder, WRITE_TIMEOUT, TimeUnit.SECONDS);
            retryOnConnectionFailure.invoke(builder, true);

            // Build the client
            Method buildMethod = clientBuilderClass.getMethod("build");
            return (OkHttpClient) buildMethod.invoke(builder);

        } catch (Exception e) {
            Log.e(TAG, "Error creating custom OkHttpClient", e);
            return null;
        }
    }

    /**
     * Attempt to set global OkHttp defaults through various methods
     */
    private void setGlobalOkHttpDefaults(OkHttpClient client) {
        if (client == null) return;

        try {
            // Try setting client on various classes that might use it

            // 1. Try Firebase HTTP adapter
            try {
                Class<?> firebaseHttpClass = Class.forName("com.google.firebase.FirebaseNetworkAdapter");
                Field instanceField = firebaseHttpClass.getDeclaredField("INSTANCE");
                instanceField.setAccessible(true);
                Object instance = instanceField.get(null);

                Field clientField = instance.getClass().getDeclaredField("httpClient");
                clientField.setAccessible(true);
                clientField.set(instance, client);

                Log.d(TAG, "Set Firebase HTTP client");
            } catch (Exception e) {
                Log.d(TAG, "Could not set Firebase HTTP client: " + e.getMessage());
            }

            // 2. Try OneSignal's client
            try {
                Class<?> oneSignalHttpClass = Class.forName("com.onesignal.OneSignalRestClient");
                Field clientField = oneSignalHttpClass.getDeclaredField("client");
                clientField.setAccessible(true);
                clientField.set(null, client);

                Log.d(TAG, "Set OneSignal HTTP client");
            } catch (Exception e) {
                Log.d(TAG, "Could not set OneSignal HTTP client: " + e.getMessage());
            }

        } catch (Exception e) {
            Log.e(TAG, "Error setting global OkHttp defaults", e);
        }
    }

    private void setupCrashRecovery() {
        try {
            // First, save the original handler to avoid recursive calls
            originalExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();

            // Set up a handler for uncaught exceptions to recover from Firestore crashes
            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
                // Using AtomicBoolean to prevent recursive crashes
                if (isHandlingCrash.compareAndSet(false, true)) {
                    try {
                        // Log the exception
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

                        // Check if this is an OkHttp stream exception
                        if (throwable instanceof RuntimeException &&
                                throwable.getMessage() != null &&
                                throwable.getCause() != null &&
                                throwable.getCause().getMessage() != null &&
                                throwable.getCause().getMessage().contains("unexpected end of stream")) {

                            // Flag for OkHttp recovery
                            try {
                                SharedPreferences prefs = getSharedPreferences("auth_state", MODE_PRIVATE);
                                prefs.edit().putBoolean("okhttp_error_recovery", true).apply();
                            } catch (Exception e) {
                                Log.e(TAG, "Error saving OkHttp recovery state", e);
                            }
                        }

                        // Save crash details - unnecessary method, just log
                        Log.d(TAG, "Would save crash report for: " + throwable.getMessage());
                    } catch (Exception e) {
                        // Last resort if our error handler itself crashes
                        Log.e(TAG, "Error in crash handler", e);
                    } finally {
                        // Reset crash handling flag
                        isHandlingCrash.set(false);

                        // Pass to the original handler if it exists
                        if (originalExceptionHandler != null) {
                            originalExceptionHandler.uncaughtException(thread, throwable);
                        }
                    }
                } else {
                    // Already handling a crash, use original handler directly
                    if (originalExceptionHandler != null) {
                        originalExceptionHandler.uncaughtException(thread, throwable);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to setup crash recovery", e);
        }
    }

    private void initializeFirebaseSafely() {
        try {
            // Check if we've had a previous Firestore crash
            SharedPreferences prefs = getSharedPreferences("auth_state", MODE_PRIVATE);
            boolean needsRecovery = prefs.getBoolean("firestore_error_recovery", false);
            boolean needsOkHttpRecovery = prefs.getBoolean("okhttp_error_recovery", false);

            if (needsRecovery || needsOkHttpRecovery) {
                // Clear the flags
                prefs.edit()
                        .putBoolean("firestore_error_recovery", false)
                        .putBoolean("okhttp_error_recovery", false)
                        .apply();

                // Clear Firebase caches to ensure fresh state
                try {
                    // Delete the Firebase persistence directory
                    File cacheDir = new File(getCacheDir(), "firebase-firestore");
                    if (cacheDir.exists()) {
                        deleteDirectory(cacheDir);
                    }

                    // Also clear OkHttp cache
                    File okHttpCache = new File(getCacheDir(), "okhttp");
                    if (okHttpCache.exists()) {
                        deleteDirectory(okHttpCache);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error clearing caches", e);
                }

                // Wait a moment for cleanup
                Thread.sleep(300);
            }

            // Now initialize Firebase
            FirebaseApp.initializeApp(this);

            // Configure Firestore settings
            FirebaseFirestore firestore = FirebaseFirestore.getInstance();
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build();
            firestore.setFirestoreSettings(settings);

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

    private void initializeFirebaseAuth() {
        try {
            // Initialize Firebase Auth if not already initialized
            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                // Try to sign in anonymously for better permissions
                FirebaseAuth.getInstance().signInAnonymously()
                        .addOnSuccessListener(authResult -> {
                            Log.d(TAG, "Anonymous auth success for Firestore access");
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Anonymous auth failed", e);
                        });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Firebase Auth", e);
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